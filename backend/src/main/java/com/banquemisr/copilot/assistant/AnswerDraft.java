package com.banquemisr.copilot.assistant;

import java.util.List;

public record AnswerDraft(String answer, List<String> citedEvidence, boolean refused, String refusalReason) {

    public static AnswerDraft refusal(String reason) {
        return new AnswerDraft(null, List.of(), true, reason);
    }
}
