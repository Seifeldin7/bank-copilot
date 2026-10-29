package com.banquemisr.copilot.knowledge;

import com.banquemisr.copilot.common.CopilotProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UploadStorage {

    private final Path directory;

    public UploadStorage(CopilotProperties properties) {
        this.directory = Path.of(properties.uploads().dir()).toAbsolutePath().normalize();
    }

    public String store(String fileName, byte[] bytes) {
        String safeName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = directory.resolve(UUID.randomUUID() + "-" + safeName);
        try {
            Files.createDirectories(directory);
            Files.write(target, bytes);
            return target.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store uploaded file", e);
        }
    }
}
