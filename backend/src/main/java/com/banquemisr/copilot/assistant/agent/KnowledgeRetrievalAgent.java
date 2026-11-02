package com.banquemisr.copilot.assistant.agent;

import com.banquemisr.copilot.assistant.CopilotDtos.AgentName;
import com.banquemisr.copilot.assistant.agent.AgentMessages.RetrievalRequest;
import com.banquemisr.copilot.assistant.agent.AgentMessages.RetrievalResult;
import com.banquemisr.copilot.common.CopilotProperties;
import com.banquemisr.copilot.retrieval.Evidence;
import com.banquemisr.copilot.retrieval.HybridSearch;
import com.banquemisr.copilot.retrieval.QueryTerms;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeRetrievalAgent implements Agent<RetrievalRequest, RetrievalResult> {

    private final HybridSearch hybridSearch;
    private final double minSimilarity;
    private final int minPassages;
    private final int maxIterations;

    public KnowledgeRetrievalAgent(HybridSearch hybridSearch, CopilotProperties properties) {
        this.hybridSearch = hybridSearch;
        this.minSimilarity = properties.retrieval().minSimilarity();
        this.minPassages = properties.agents().minPassages();
        this.maxIterations = properties.agents().retrievalMaxIterations();
    }

    @Override
    public AgentName name() {
        return AgentName.KNOWLEDGE_RETRIEVAL;
    }

    @Override
    public AgentResult<RetrievalResult> run(RetrievalRequest request) {
        Set<String> queries = new LinkedHashSet<>();
        queries.add(request.question());
        queries.add(String.join(" ", QueryTerms.of(request.question())));
        queries.removeIf(String::isBlank);

        List<Evidence> best = List.of();
        String bestQuery = request.question();
        int iterations = 0;
        for (String query : queries) {
            if (iterations == maxIterations || relevant(best) >= minPassages) {
                break;
            }
            iterations++;
            List<Evidence> found = hybridSearch.search(query);
            if (iterations == 1 || relevant(found) > relevant(best)) {
                best = found;
                bestQuery = query;
            }
        }

        String summary = "Hybrid search (semantic + keyword, fused by RRF) returned %d passages, %d above similarity %.2f"
                .formatted(best.size(), relevant(best), minSimilarity)
                + (iterations > 1 ? "; reformulated the query to keywords once" : "");
        return new AgentResult<>(new RetrievalResult(best, bestQuery), summary, iterations, List.of());
    }

    private long relevant(List<Evidence> evidence) {
        return evidence.stream().filter(item -> item.similarity() >= minSimilarity).count();
    }
}
