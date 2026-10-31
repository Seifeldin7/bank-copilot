package com.banquemisr.copilot.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.banquemisr.copilot.retrieval.Evidence;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StubChatModelTest {

    private final StubChatModel stub = new StubChatModel(JsonMapper.builder().build());

    @Test
    void answersFromThePassageThatCoversTheQuestionAndCitesIt() {
        AnswerDraft draft = stub.answer(prompt("What is the debit card replacement fee?",
                "Standing orders can be cancelled at any branch.",
                "The debit card replacement fee is EGP 50. Cards are ready in 5 working days."));

        assertThat(draft.refused()).isFalse();
        assertThat(draft.citedEvidence()).containsExactly("E2");
        assertThat(draft.answer()).contains("EGP 50").endsWith("[E2]");
    }

    @Test
    void refusesWhenNoPassageCoversTheQuestion() {
        AnswerDraft draft = stub.answer(prompt("What is the bank's share price today?",
                "The debit card replacement fee is EGP 50."));

        assertThat(draft.refused()).isTrue();
        assertThat(draft.refusalReason()).isEqualTo(StubChatModel.NO_MATCH);
    }

    private static String prompt(String question, String... passages) {
        Map<String, Evidence> byId = new LinkedHashMap<>();
        for (int i = 0; i < passages.length; i++) {
            byId.put("E" + (i + 1), new Evidence(UUID.randomUUID(), UUID.randomUUID(), "Doc", 1, 1, null, passages[i],
                    0.6, 1, 1, 0.03));
        }
        return GroundedAnswerService.userPrompt(question, byId);
    }
}
