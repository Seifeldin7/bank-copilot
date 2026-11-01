package com.banquemisr.copilot.servicerequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.banquemisr.copilot.servicerequest.RequestPayload.AdditionalDocumentation;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardIssue;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardService;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestDiffTest {

    @Test
    void listsChangedFieldsWithOldAndNewValues() {
        RequestDiff.Content before = new RequestDiff.Content("Replace card", "Damaged chip.", RequestFixtures.card());
        RequestDiff.Content after = new RequestDiff.Content("Replace card", "Damaged chip.",
                new CardService("CUS-104233", "4822", CardIssue.LOST, "CAI-012", ""));

        assertThat(RequestDiff.between(before, after)).containsExactly(
                new FieldChange("payload.cardLast4", "4821", "4822"),
                new FieldChange("payload.issueType", CardIssue.REPLACEMENT, CardIssue.LOST));
    }

    @Test
    void treatsBlankTextAndEmptyListsAsNoValue() {
        RequestDiff.Content before = new RequestDiff.Content("Missing documents ", "Bring them.",
                new AdditionalDocumentation("CUS-104233", null, List.of(), "2026-06-20", ""));
        RequestDiff.Content after = new RequestDiff.Content("Missing documents", "Bring them.",
                new AdditionalDocumentation("CUS-104233", " ", null, "2026-06-20", null));

        assertThat(RequestDiff.between(before, after)).isEmpty();
    }
}
