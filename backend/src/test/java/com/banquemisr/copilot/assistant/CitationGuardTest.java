package com.banquemisr.copilot.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CitationGuardTest {

    private final Set<String> evidence = Set.of("E1", "E2", "E3");

    @Test
    void renumbersCitationsInOrderOfFirstUse() {
        AnswerDraft draft = new AnswerDraft("The fee is EGP 50 [E3]. Bring an ID [e1, E3].", List.of("E3", "E1"), false, null);

        CitationGuard.Checked checked = CitationGuard.check(draft, evidence);

        assertThat(checked.refused()).isFalse();
        assertThat(checked.citedIds()).containsExactly("E3", "E1");
        assertThat(checked.answer()).isEqualTo("The fee is EGP 50 [1]. Bring an ID [2][1].");
    }

    @Test
    void withholdsAnAnswerThatCitesAPassageThatWasNotRetrieved() {
        AnswerDraft draft = new AnswerDraft("Replacement takes 3 days [E1]. It is free [E7].", List.of("E1", "E7"), false, null);

        assertThat(CitationGuard.check(draft, evidence).refusalReason()).isEqualTo(CitationGuard.UNKNOWN_SOURCE);
    }

    @Test
    void withholdsAnAnswerWhoseCitationListNamesAnUnknownPassage() {
        AnswerDraft draft = new AnswerDraft("Replacement takes 3 days [E1].", List.of("E1", "E9"), false, null);

        assertThat(CitationGuard.check(draft, evidence).refusalReason()).isEqualTo(CitationGuard.UNKNOWN_SOURCE);
    }

    @Test
    void withholdsAnAnswerWithoutCitations() {
        AnswerDraft draft = new AnswerDraft("Replacement takes 3 days.", List.of("E1"), false, null);

        assertThat(CitationGuard.check(draft, evidence).refusalReason()).isEqualTo(CitationGuard.NOT_LINKED);
    }

    @Test
    void keepsTheModelsOwnRefusalReason() {
        CitationGuard.Checked checked = CitationGuard.check(AnswerDraft.refusal("Not covered by the documents."), evidence);

        assertThat(checked.refused()).isTrue();
        assertThat(checked.refusalReason()).isEqualTo("Not covered by the documents.");
    }
}
