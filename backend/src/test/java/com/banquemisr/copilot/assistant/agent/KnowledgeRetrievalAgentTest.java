package com.banquemisr.copilot.assistant.agent;

import static com.banquemisr.copilot.assistant.AssistantFixtures.evidence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banquemisr.copilot.assistant.AssistantFixtures;
import com.banquemisr.copilot.assistant.agent.AgentMessages.RetrievalRequest;
import com.banquemisr.copilot.assistant.agent.AgentMessages.RetrievalResult;
import com.banquemisr.copilot.retrieval.HybridSearch;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeRetrievalAgentTest {

    private final HybridSearch search = mock(HybridSearch.class);
    private final KnowledgeRetrievalAgent agent = new KnowledgeRetrievalAgent(search, AssistantFixtures.properties());

    @Test
    void stopsAfterOneSearchWhenEnoughPassagesAreRelevant() {
        when(search.search(anyString())).thenReturn(List.of(evidence(0.7), evidence(0.6)));

        AgentResult<RetrievalResult> result = agent.run(new RetrievalRequest("What is the debit card replacement fee?"));

        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.output().evidence()).hasSize(2);
        assertThat(result.modelCalls()).isEmpty();
        verify(search, times(1)).search(anyString());
    }

    @Test
    void reformulatesToKeywordsOnceAndKeepsTheBetterResult() {
        when(search.search("How long until a replacement card is ready?")).thenReturn(List.of(evidence(0.3)));
        when(search.search("long until replacement card ready")).thenReturn(List.of(evidence(0.6), evidence(0.5)));

        AgentResult<RetrievalResult> result = agent.run(new RetrievalRequest("How long until a replacement card is ready?"));

        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.output().query()).isEqualTo("long until replacement card ready");
        assertThat(result.output().evidence()).hasSize(2);
        assertThat(result.summary()).contains("reformulated");
    }

    @Test
    void neverSearchesMoreThanTheIterationLimit() {
        when(search.search(anyString())).thenReturn(List.of());

        AgentResult<RetrievalResult> result = agent.run(new RetrievalRequest("Who won the football match yesterday?"));

        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.output().evidence()).isEmpty();
        verify(search, times(2)).search(anyString());
    }
}
