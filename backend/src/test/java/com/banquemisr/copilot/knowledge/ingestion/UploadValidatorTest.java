package com.banquemisr.copilot.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.knowledge.Document;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UploadValidatorTest {

    private final UploadValidator validator = new UploadValidator();

    @Test
    void acceptsMarkdownAndPdfWithMatchingContent() {
        assertThat(validator.validate("guide.md", "# Guide".getBytes(StandardCharsets.UTF_8))).isEqualTo(Document.Format.MARKDOWN);
        assertThat(validator.validate("policy.PDF", "%PDF-1.7 rest".getBytes(StandardCharsets.US_ASCII))).isEqualTo(Document.Format.PDF);
    }

    @Test
    void rejectsUnsupportedExtensions() {
        assertRejected("notes.docx", "hello".getBytes(StandardCharsets.UTF_8), "Only .pdf and .md files are accepted.");
    }

    @Test
    void rejectsPdfWhoseContentIsNotAPdf() {
        assertRejected("fake.pdf", "MZ executable".getBytes(StandardCharsets.US_ASCII), "This file is not a valid PDF.");
    }

    @Test
    void rejectsMarkdownThatIsNotPlainText() {
        assertRejected("binary.md", new byte[]{(byte) 0xC3, (byte) 0x28}, "Markdown files must be plain UTF-8 text.");
        assertRejected("nul.md", new byte[]{'a', 0, 'b'}, "Markdown files must be plain UTF-8 text.");
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertRejected("empty.md", new byte[0], "The file is empty.");
        assertRejected("big.md", new byte[(int) UploadValidator.MAX_BYTES + 1], "Files must be 10 MB or smaller.");
    }

    private void assertRejected(String fileName, byte[] bytes, String message) {
        assertThatThrownBy(() -> validator.validate(fileName, bytes))
                .isInstanceOf(ApiException.class)
                .hasMessage(message);
    }
}
