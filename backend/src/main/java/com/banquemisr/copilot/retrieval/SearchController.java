package com.banquemisr.copilot.retrieval;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.common.FieldError;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final int MAX_RESULTS = 20;

    private final HybridSearch hybridSearch;

    public SearchController(HybridSearch hybridSearch) {
        this.hybridSearch = hybridSearch;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<Evidence> search(@RequestParam("q") String query,
                                 @RequestParam(value = "limit", defaultValue = "10") int limit) {
        if (query.isBlank() || query.length() > 1000) {
            throw ApiException.badRequest("Invalid search.", new FieldError("q", "Enter 1 to 1000 characters."));
        }
        return hybridSearch.search(query, Math.clamp(limit, 1, MAX_RESULTS));
    }
}
