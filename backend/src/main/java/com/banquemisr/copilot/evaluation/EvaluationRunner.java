package com.banquemisr.copilot.evaluation;

import com.banquemisr.copilot.evaluation.EvaluationApi.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class EvaluationRunner {

    private static final int K = 5;

    private record QuestionOutcome(CaseResult result, boolean hasDocuments, boolean hit, boolean answered,
                                   boolean grounded, boolean refusalExpected, boolean refusalCorrect) {
    }

    private final EvaluationApi api;
    private final String employee;
    private final String supervisor;

    private EvaluationRunner(EvaluationApi api) {
        this.api = api;
        this.employee = api.login("employee1", "Employee@123");
        this.supervisor = api.login("supervisor1", "Supervisor@123");
    }

    public static void main(String[] args) throws IOException {
        String baseUrl = System.getenv().getOrDefault("EVAL_BASE_URL", "http://localhost:8080");
        Path cases = Path.of(System.getenv().getOrDefault("EVAL_CASES", "../eval/cases.json"));
        Path output = Path.of(System.getenv().getOrDefault("EVAL_OUTPUT", "../eval/results"));
        JsonMapper json = JsonMapper.builder().build();
        EvaluationApi api = new EvaluationApi(baseUrl, json);

        EvaluationRunner runner = new EvaluationRunner(api);
        List<QuestionOutcome> questions = new ArrayList<>();
        for (JsonNode question : json.readTree(cases.toFile()).path("questions")) {
            questions.add(runner.evaluate(question));
        }
        List<CaseResult> workflow = new WorkflowScenarios(api).runAll();

        Map<String, Object> report = runner.report(baseUrl, questions, workflow);
        Files.createDirectories(output);
        Files.writeString(output.resolve("report.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        Files.writeString(output.resolve("report.md"), markdown(report, questions, workflow));
        System.out.println(markdown(report, questions, workflow));
    }

    private QuestionOutcome evaluate(JsonNode testCase) {
        String question = testCase.path("question").asString();
        JsonNode expect = testCase.path("expect");
        Response ask = api.post("/api/copilot/ask", employee, Map.of("question", question));
        JsonNode body = ask.body();

        List<String> problems = new ArrayList<>();
        boolean refused = body.path("refused").asBoolean();
        boolean refusalExpected = expect.has("refused");
        boolean refusalCorrect = !refusalExpected || refused == expect.path("refused").asBoolean();
        if (ask.status() != 200) {
            problems.add("HTTP " + ask.status());
        }
        if (!refusalCorrect) {
            problems.add(refused ? "refused: " + body.path("refusalReason").asString() : "answered when a refusal was expected");
        }
        if (expect.has("mode") && !expect.path("mode").asString().equals(body.path("mode").asString())) {
            problems.add("mode " + body.path("mode").asString());
        }

        Set<String> expectedDocs = strings(expect.path("documents"));
        Set<String> cited = new TreeSet<>();
        boolean versionOk = true;
        for (JsonNode citation : body.path("citations")) {
            cited.add(citation.path("documentTitle").asString());
            if (expectedDocs.contains(citation.path("documentTitle").asString())
                    && citation.path("version").asInt() < expect.path("minVersion").asInt(1)) {
                versionOk = false;
            }
        }
        boolean hasDocuments = !expectedDocs.isEmpty();
        boolean answered = !refused && ask.status() == 200;
        boolean grounded = answered && cited.stream().anyMatch(expectedDocs::contains) && versionOk;
        boolean hit = hasDocuments && topTitles(question).stream().anyMatch(expectedDocs::contains);
        if (hasDocuments && answered && !grounded) {
            problems.add("cited " + cited + (versionOk ? "" : " with an outdated version"));
        }

        String answer = body.path("answer").asString("").toLowerCase(Locale.ROOT);
        for (String phrase : strings(expect.path("mustNotContain"))) {
            if (answer.contains(phrase.toLowerCase(Locale.ROOT))) {
                problems.add("answer contains \"" + phrase + "\"");
            }
        }

        String actual = refused ? "refused (" + body.path("mode").asString() + ")"
                : "answered (" + body.path("mode").asString() + ") citing " + cited;
        CaseResult result = new CaseResult(testCase.path("id").asString(), testCase.path("category").asString(), question,
                problems.isEmpty(), expectation(expect), problems.isEmpty() ? actual : actual + "; " + String.join("; ", problems));
        return new QuestionOutcome(result, hasDocuments, hit, answered, grounded, refusalExpected, refusalCorrect);
    }

    private List<String> topTitles(String question) {
        List<String> titles = new ArrayList<>();
        for (JsonNode hit : api.get("/api/search?limit=" + K + "&q=" + EvaluationApi.query(question), supervisor).body()) {
            titles.add(hit.path("documentTitle").asString());
        }
        return titles;
    }

    private Map<String, Object> report(String baseUrl, List<QuestionOutcome> questions, List<CaseResult> workflow) {
        long withDocs = questions.stream().filter(QuestionOutcome::hasDocuments).count();
        long hits = questions.stream().filter(q -> q.hasDocuments() && q.hit()).count();
        long answeredWithDocs = questions.stream().filter(q -> q.hasDocuments() && q.answered()).count();
        long grounded = questions.stream().filter(QuestionOutcome::grounded).count();
        long refusalCases = questions.stream().filter(QuestionOutcome::refusalExpected).count();
        long refusalCorrect = questions.stream().filter(q -> q.refusalExpected() && q.refusalCorrect()).count();
        long workflowPassed = workflow.stream().filter(CaseResult::passed).count();
        List<CaseResult> all = new ArrayList<>(questions.stream().map(QuestionOutcome::result).toList());
        all.addAll(workflow);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("retrievalHitRateAt" + K, ratio(hits, withDocs));
        metrics.put("groundedness", ratio(grounded, answeredWithDocs));
        metrics.put("answerRateOnAnswerableQuestions", ratio(answeredWithDocs, withDocs));
        metrics.put("refusalCorrectness", ratio(refusalCorrect, refusalCases));
        metrics.put("workflowAndSecurityPassRate", ratio(workflowPassed, workflow.size()));
        metrics.put("casesPassed", all.stream().filter(CaseResult::passed).count() + "/" + all.size());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ranAt", Instant.now().toString());
        report.put("baseUrl", baseUrl);
        report.put("metrics", metrics);
        report.put("cases", all);
        return report;
    }

    @SuppressWarnings("unchecked")
    private static String markdown(Map<String, Object> report, List<QuestionOutcome> questions, List<CaseResult> workflow) {
        StringBuilder md = new StringBuilder("# Evaluation report\n\n")
                .append("Run at ").append(report.get("ranAt")).append(" against ").append(report.get("baseUrl")).append(".\n\n")
                .append("| Metric | Value |\n|---|---|\n");
        ((Map<String, Object>) report.get("metrics")).forEach((name, value) ->
                md.append("| ").append(name).append(" | ").append(value).append(" |\n"));

        md.append("\n## Question cases\n\n| ID | Category | Question | Expected | Actual | Result |\n|---|---|---|---|---|---|\n");
        questions.forEach(q -> row(md, q.result()));
        md.append("\n## Workflow and security cases\n\n| ID | Category | Scenario | Expected | Actual | Result |\n|---|---|---|---|---|---|\n");
        workflow.forEach(result -> row(md, result));

        List<CaseResult> failures = new ArrayList<>(questions.stream().map(QuestionOutcome::result).filter(r -> !r.passed()).toList());
        failures.addAll(workflow.stream().filter(r -> !r.passed()).toList());
        md.append("\n## Failures\n\n");
        if (failures.isEmpty()) {
            md.append("None.\n");
        }
        failures.forEach(f -> md.append("- **").append(f.id()).append("** ").append(f.description()).append(": ").append(f.actual()).append("\n"));
        return md.toString();
    }

    private static void row(StringBuilder md, CaseResult result) {
        md.append("| ").append(result.id()).append(" | ").append(result.category()).append(" | ").append(cell(result.description()))
                .append(" | ").append(cell(result.expected())).append(" | ").append(cell(result.actual()))
                .append(" | ").append(result.passed() ? "pass" : "**fail**").append(" |\n");
    }

    private static String expectation(JsonNode expect) {
        List<String> parts = new ArrayList<>();
        if (expect.has("refused")) {
            parts.add(expect.path("refused").asBoolean() ? "refuse" : "answer");
        }
        if (expect.has("mode")) {
            parts.add("mode " + expect.path("mode").asString());
        }
        if (expect.has("documents")) {
            parts.add("cite one of " + strings(expect.path("documents")));
        }
        if (expect.has("minVersion")) {
            parts.add("version >= " + expect.path("minVersion").asInt());
        }
        if (expect.has("mustNotContain")) {
            parts.add("never repeat the injected text");
        }
        return String.join(", ", parts);
    }

    private static Set<String> strings(JsonNode array) {
        Set<String> values = new TreeSet<>();
        array.forEach(item -> values.add(item.asString()));
        return values;
    }

    private static String ratio(long part, long whole) {
        return whole == 0 ? "n/a" : "%d/%d (%.0f%%)".formatted(part, whole, 100.0 * part / whole);
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
