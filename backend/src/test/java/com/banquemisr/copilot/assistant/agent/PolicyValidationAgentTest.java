package com.banquemisr.copilot.assistant.agent;

import static com.banquemisr.copilot.assistant.AssistantFixtures.evidence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.banquemisr.copilot.assistant.AssistantFixtures;
import com.banquemisr.copilot.assistant.CopilotDtos.ValidationVerdict;
import com.banquemisr.copilot.assistant.ModelGateway;
import com.banquemisr.copilot.assistant.agent.AgentMessages.ValidationRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PolicyValidationAgentTest {

    private final ModelGateway gateway = mock(ModelGateway.class);
    private final PolicyValidationAgent agent = new PolicyValidationAgent(gateway, JsonMapper.builder().build(), AssistantFixtures.properties());

    @Test
    void withoutARelevantPassageTheEvidenceIsInsufficientAndNoModelIsCalled() {
        AgentResult<ValidationVerdict> result = agent.run(new ValidationRequest("What is the capital of France?", List.of(evidence(0.12))));

        assertThat(result.output().sufficient()).isFalse();
        assertThat(result.output().missingInformation()).containsExactly(PolicyValidationAgent.NO_RELEVANT_PASSAGE);
        assertThat(result.modelCalls()).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void readsTheModelsVerdictAndClampsTheConfidence() {
        modelReplies("""
                {"sufficient": false, "missingInformation": ["The fee for premium cards"], "unsupportedAssumptions": ["The card is premium"], "confidence": 1.7}""");

        AgentResult<ValidationVerdict> result = agent.run(new ValidationRequest("Is the premium card fee waived?", List.of(evidence(0.6))));

        assertThat(result.output()).isEqualTo(new ValidationVerdict(false, List.of("The fee for premium cards"), List.of("The card is premium"), 1.0));
        assertThat(result.modelCalls()).hasSize(1);
    }

    @Test
    void anUnreadableReplyIsTreatedAsInsufficient() {
        modelReplies("Looks fine to me!");

        ValidationVerdict verdict = agent.run(new ValidationRequest("What is the fee?", List.of(evidence(0.6)))).output();

        assertThat(verdict.sufficient()).isFalse();
        assertThat(verdict.unsupportedAssumptions()).containsExactly(PolicyValidationAgent.UNREADABLE);
    }

    private void modelReplies(String text) {
        when(gateway.call(eq(PolicyValidationAgent.SYSTEM_PROMPT), anyString())).thenReturn(AssistantFixtures.reply(text));
    }
}
