package com.banquemisr.copilot.assistant.agent;

import com.banquemisr.copilot.retrieval.Evidence;
import com.banquemisr.copilot.servicerequest.RequestCategory;
import com.banquemisr.copilot.servicerequest.RequestPayload;
import java.util.List;

public final class AgentMessages {

    private AgentMessages() {
    }

    public record RetrievalRequest(String question) {
    }

    public record RetrievalResult(List<Evidence> evidence, String query) {
    }

    public record ValidationRequest(String question, List<Evidence> evidence) {
    }

    public record PreparationRequest(String question, String answer, RequestCategory category, String branchCode) {
    }

    public record RequestDraft(String title, String description, RequestPayload payload, List<String> missingFields) {
    }
}
