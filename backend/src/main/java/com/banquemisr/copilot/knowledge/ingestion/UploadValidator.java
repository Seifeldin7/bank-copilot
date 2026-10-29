package com.banquemisr.copilot.knowledge.ingestion;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.common.FieldError;
import com.banquemisr.copilot.knowledge.Document;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class UploadValidator {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    public Document.Format validate(String fileName, byte[] bytes) {
        if (fileName == null || fileName.isBlank()) {
            throw invalid("A file name is required.");
        }
        Document.Format format = formatOf(fileName);
        if (bytes.length == 0) {
            throw invalid("The file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw invalid("Files must be 10 MB or smaller.");
        }
        if (format == Document.Format.PDF && !startsWithPdfHeader(bytes)) {
            throw invalid("This file is not a valid PDF.");
        }
        if (format == Document.Format.MARKDOWN && !isPlainUtf8Text(bytes)) {
            throw invalid("Markdown files must be plain UTF-8 text.");
        }
        return format;
    }

    public static Document.Format formatOf(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return Document.Format.PDF;
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return Document.Format.MARKDOWN;
        }
        throw invalid("Only .pdf and .md files are accepted.");
    }

    private static boolean startsWithPdfHeader(byte[] bytes) {
        return bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
    }

    private static boolean isPlainUtf8Text(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return text.indexOf('\u0000') < 0;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest(message, new FieldError("file", message));
    }
}
