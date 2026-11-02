package com.banquemisr.copilot.assistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.banquemisr.copilot.assistant.AssistantFixtures;
import com.banquemisr.copilot.assistant.ModelGateway;
import com.banquemisr.copilot.assistant.agent.AgentMessages.PreparationRequest;
import com.banquemisr.copilot.assistant.agent.AgentMessages.RequestDraft;
import com.banquemisr.copilot.servicerequest.RequestCategory;
import com.banquemisr.copilot.servicerequest.RequestFixtures;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardIssue;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RequestPreparationAgentTest {

    private final ModelGateway gateway = mock(ModelGateway.class);
    private final RequestPreparationAgent agent = new RequestPreparationAgent(gateway, JsonMapper.builder().build(),
            new CategorySchemaCatalog(), RequestFixtures.validator());
    private final PreparationRequest request = new PreparationRequest("Customer CUS-104233 lost their debit card, what now?",
            "Block the card first [1].", RequestCategory.CARD_SERVICE, "CAI-012");

    @Test
    void turnsTheModelReplyIntoATypedDraftAndListsWhatIsStillMissing() {
        modelReplies("""
                {"title": "Replace lost debit card", "description": "Customer lost their debit card and needs a replacement.",
                 "payload": {"customerReference": "CUS-104233", "cardLast4": "", "issueType": "LOST", "deliveryBranch": "CAI-012"}}""");

        RequestDraft draft = agent.run(request).output();

        assertThat(draft.payload()).isEqualTo(new CardService("CUS-104233", "", CardIssue.LOST, "CAI-012", null));
        assertThat(draft.title()).isEqualTo("Replace lost debit card");
        assertThat(draft.missingFields()).containsExactly("payload.cardLast4");
    }

    @Test
    void anUnreadableReplyStillGivesAnEmptyDraftForTheCategory() {
        modelReplies("I cannot help with that.");

        AgentResult<RequestDraft> result = agent.run(request);

        assertThat(result.output().payload()).isInstanceOf(CardService.class);
        assertThat(result.output().title()).isEqualTo("Customer CUS-104233 lost their debit card, what now");
        assertThat(result.output().missingFields()).contains("payload.customerReference", "payload.cardLast4");
        assertThat(result.summary()).startsWith("Model reply unreadable");
    }

    private void modelReplies(String text) {
        when(gateway.call(eq(RequestPreparationAgent.SYSTEM_PROMPT), anyString())).thenReturn(AssistantFixtures.reply(text));
    }
}
