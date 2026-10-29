package com.banquemisr.copilot.knowledge;

import com.banquemisr.copilot.identity.CurrentUser;
import com.banquemisr.copilot.knowledge.ingestion.IngestionReport;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUser currentUser;

    public DocumentController(DocumentService documentService, CurrentUser currentUser) {
        this.documentService = documentService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return documentService.list();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public IngestionReport upload(@RequestPart("file") MultipartFile file,
                                  @RequestParam(value = "title", required = false) String title,
                                  @RequestParam(value = "category", required = false) String category,
                                  @AuthenticationPrincipal Jwt jwt) throws IOException {
        return documentService.upload(file.getOriginalFilename(), file.getBytes(), title, category, currentUser.from(jwt));
    }

    @GetMapping("/{id}/ingestion-log")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<StageResult> ingestionLog(@PathVariable UUID id) {
        return documentService.ingestionLog(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public DocumentSummary retire(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return documentService.retire(id, currentUser.from(jwt));
    }
}
