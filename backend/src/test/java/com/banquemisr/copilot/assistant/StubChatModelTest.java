package com.banquemisr.copilot.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.banquemisr.copilot.assistant.CopilotDtos.ValidationVerdict;
import com.banquemisr.copilot.retrieval.Evidence;
import java.util.LinkedHashMap;
import java.util.Map;
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
        AnswerDraft draft = stub.answer(prompt("What is the bank's share price today?", "The debit card replacement fee is EGP 50."));

        assertThat(draft.refused()).isTrue();
        assertThat(draft.refusalReason()).isEqualTo(StubChatModel.NO_MATCH);
    }

    @Test
    void validationNamesTheTermsTheDocumentsNeverMention() {
        ValidationVerdict covered = stub.validate(prompt("What is the debit card replacement fee?", "The debit card replacement fee is EGP 50."));
        ValidationVerdict missing = stub.validate(prompt("What is the daily ATM withdrawal limit?", "The debit card replacement fee is EGP 50."));

        assertThat(covered.sufficient()).isTrue();
        assertThat(missing.sufficient()).isFalse();
        assertThat(missing.missingInformation()).singleElement().asString().contains("daily", "atm", "withdrawal", "limit");
    }

    @Test
    void draftingPullsOnlyValuesStatedInTheQuestion() {
        Map<String, Object> draft = new StubRequestDrafter().draft("""
                <category>CARD_SERVICE</category>
                <question>Customer CUS-104233 lost the card ending 4821, how do I replace it?</question>
                Branch code of the employee: CAI-012
                """);

        assertThat(draft.get("payload")).isEqualTo(Map.of("customerReference", "CUS-104233", "cardLast4", "4821",
                "issueType", "LOST", "deliveryBranch", "CAI-012", "notes", ""));
    }

    private static String prompt(String question, String... passages) {
        Map<String, Evidence> byId = new LinkedHashMap<>();
        for (int i = 0; i < passages.length; i++) {
            byId.put("E" + (i + 1), AssistantFixtures.evidence("Doc", "Cards", passages[i], 0.6));
        }
        return EvidencePrompt.format(question, byId);
    }
}
