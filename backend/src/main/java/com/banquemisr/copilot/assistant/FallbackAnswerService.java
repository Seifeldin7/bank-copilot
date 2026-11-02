package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.assistant.GroundedAnswerService.AnswerResult;
import com.banquemisr.copilot.assistant.agent.AgentResult;
import com.banquemisr.copilot.retrieval.Evidence;
import com.banquemisr.copilot.retrieval.HybridSearch;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FallbackAnswerService {

    public record Result(List<Evidence> evidence, AnswerResult answer, String refusalReason) {
    }

    private final HybridSearch hybridSearch;
    private final PreCheck preCheck;
    private final GroundedAnswerService groundedAnswers;

    public FallbackAnswerService(HybridSearch hybridSearch, PreCheck preCheck, GroundedAnswerService groundedAnswers) {
        this.hybridSearch = hybridSearch;
        this.preCheck = preCheck;
        this.groundedAnswers = groundedAnswers;
    }

    public AgentResult<Result> answer(String question) {
        List<Evidence> evidence = hybridSearch.search(question);
        Optional<String> refusal = preCheck.afterSearch(evidence);
        if (refusal.isPresent()) {
            return new AgentResult<>(new Result(evidence, null, refusal.get()),
                    "Plain hybrid search found no passage above the similarity threshold; refused", 1, List.of());
        }
        AnswerResult answer = groundedAnswers.answer(question, evidence);
        String summary = answer.refused()
                ? "Plain retrieval and grounded answer; refused: " + answer.refusalReason()
                : "Plain retrieval and grounded answer with " + answer.citations().size() + " verified citation(s)";
        return new AgentResult<>(new Result(evidence, answer, answer.refusalReason()), summary, 1, List.of(answer.reply()));
    }
}
