package com.banquemisr.copilot.knowledge;

import com.banquemisr.copilot.common.CopilotProperties;
import com.banquemisr.copilot.knowledge.ingestion.IngestionPipeline;
import com.banquemisr.copilot.knowledge.ingestion.IngestionReport;
import com.banquemisr.copilot.knowledge.ingestion.IngestionRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CorpusBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(CorpusBootstrapper.class);

    private final IngestionPipeline pipeline;
    private final CopilotProperties properties;
    private final CorpusStatus status;

    public CorpusBootstrapper(IngestionPipeline pipeline, CopilotProperties properties, CorpusStatus status) {
        this.pipeline = pipeline;
        this.properties = properties;
        this.status = status;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncCorpus() {
        if (!properties.corpus().bootstrapEnabled()) {
            status.markReady();
            return;
        }
        Path root = Path.of(properties.corpus().dir()).toAbsolutePath().normalize();
        try {
            Map<String, CatalogEntry> catalog = readCatalog(root.resolve("catalog.csv"));
            for (Path file : corpusFiles(root)) {
                ingest(root, file, catalog);
            }
        } catch (IOException e) {
            log.error("Corpus sync failed for {}", root, e);
        } finally {
            status.markReady();
        }
    }

    private void ingest(Path root, Path file, Map<String, CatalogEntry> catalog) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        CatalogEntry entry = catalog.get(relative);
        try {
            IngestionReport report = pipeline.ingest(new IngestionRequest(
                    file.getFileName().toString(),
                    Files.readAllBytes(file),
                    entry == null ? null : entry.title(),
                    entry == null ? null : entry.category(),
                    Document.Source.CORPUS,
                    file.toString(),
                    null));
            log.info("Corpus file {}: {} (v{}, {} chunks)", relative, report.outcome(), report.version(), report.chunks());
        } catch (Exception e) {
            log.error("Could not ingest corpus file {}", relative, e);
        }
    }

    static List<Path> corpusFiles(Path root) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(path -> isUnder(root, path, "markdown") || isUnder(root, path, "pdf"))
                    .filter(path -> path.toString().endsWith(".md") || path.toString().endsWith(".pdf"))
                    .toList();
        }
        return files.stream()
                .sorted(Comparator.comparing((Path path) -> !path.toString().contains("/versions/"))
                        .thenComparing(Path::toString))
                .toList();
    }

    private static boolean isUnder(Path root, Path path, String folder) {
        return path.startsWith(root.resolve(folder));
    }

    private Map<String, CatalogEntry> readCatalog(Path catalogFile) throws IOException {
        Map<String, CatalogEntry> catalog = new HashMap<>();
        if (!Files.exists(catalogFile)) {
            return catalog;
        }
        List<String> lines = Files.readAllLines(catalogFile, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            String[] parts = line.split(",", 3);
            if (parts.length == 3) {
                catalog.put(parts[0].trim(), new CatalogEntry(parts[1].trim(), parts[2].trim()));
            }
        }
        return catalog;
    }

    private record CatalogEntry(String category, String title) {
    }
}
