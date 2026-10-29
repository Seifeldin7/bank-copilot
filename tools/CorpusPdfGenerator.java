import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public class CorpusPdfGenerator {

    private static final float MARGIN = 60;
    private static final float BODY_SIZE = 10.5f;
    private static final float LEADING = 14.5f;
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Path.of(args.length > 0 ? args[0] : "corpus/pdf-sources");
        Path targetRoot = Path.of(args.length > 1 ? args[1] : "corpus/pdf");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".md")).sorted().toList()) {
                Path target = targetRoot.resolve(sourceRoot.relativize(source).toString().replaceAll("\\.md$", ".pdf"));
                Files.createDirectories(target.getParent());
                render(Files.readString(source, StandardCharsets.UTF_8), target);
                System.out.println("Rendered " + target);
            }
        }
    }

    private static void render(String markdown, Path target) throws IOException {
        String text = toAscii(markdown);
        String title = text.lines().filter(line -> line.startsWith("# ")).findFirst().map(line -> line.substring(2).trim()).orElse("Document");
        String version = text.lines().filter(line -> line.startsWith("Version:")).findFirst().map(String::trim).orElse("Version: 1");
        String[] pages = text.split("(?m)^\\[\\[page\\]\\]\\s*$");

        try (PDDocument pdf = new PDDocument()) {
            PDDocumentInformation info = pdf.getDocumentInformation();
            info.setTitle(title);
            info.setAuthor("Synthetic training corpus");
            Calendar fixedDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            fixedDate.clear();
            fixedDate.set(2026, Calendar.JANUARY, 1);
            info.setCreationDate(fixedDate);
            info.setModificationDate(fixedDate);

            for (int index = 0; index < pages.length; index++) {
                PDPage page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    float top = page.getMediaBox().getHeight() - MARGIN;
                    float width = page.getMediaBox().getWidth() - 2 * MARGIN;
                    line(content, ITALIC, 8.5f, MARGIN, top + 25, title + "  |  " + version);
                    line(content, ITALIC, 8.5f, MARGIN, MARGIN - 25,
                            "Synthetic training material - Page " + (index + 1) + " of " + pages.length);
                    writeBody(content, pages[index], MARGIN, top, width, title);
                }
            }
            pdf.save(target.toFile());
        }
    }

    private static void writeBody(PDPageContentStream content, String body, float x, float top, float width, String title) throws IOException {
        float y = top;
        for (String raw : body.strip().split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                y -= LEADING * 0.5f;
                continue;
            }
            PDType1Font font = REGULAR;
            float size = BODY_SIZE;
            float indent = 0;
            if (line.startsWith("# ")) {
                font = BOLD;
                size = 17;
                line = line.substring(2);
            } else if (line.startsWith("## ")) {
                font = BOLD;
                size = 12.5f;
                line = line.substring(3);
                y -= LEADING * 0.4f;
            } else if (line.startsWith("- ")) {
                indent = 12;
            }
            for (String wrapped : wrap(line, font, size, width - indent)) {
                if (y < MARGIN) {
                    throw new IllegalStateException("Page content overflows in " + title + ": shorten the page");
                }
                line(content, font, size, x + indent, y, wrapped);
                y -= size > BODY_SIZE ? size * 1.45f : LEADING;
            }
        }
    }

    private static List<String> wrap(String text, PDType1Font font, float size, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.getStringWidth(candidate) / 1000 * size > width && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static void line(PDPageContentStream content, PDType1Font font, float size, float x, float y, String text) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private static String toAscii(String text) {
        return text.replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"')
                .replace("–", "-").replace("—", "-").replace("→", "->").replace("≤", "<=")
                .replace("≥", ">=").replace("•", "-").replace(' ', ' ')
                .replaceAll("[^\\x09\\x0A\\x0D\\x20-\\x7E]", "");
    }
}
