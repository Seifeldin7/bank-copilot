package com.banquemisr.copilot.evaluation;

import com.banquemisr.copilot.evaluation.EvaluationApi.Response;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

class WorkflowScenarios {

    private final EvaluationApi api;
    private final String employee1;
    private final String employee2;
    private final String supervisor1;
    private final String supervisor2;
    private JsonNode executedRequest;

    WorkflowScenarios(EvaluationApi api) {
        this.api = api;
        this.employee1 = api.login("employee1", "Employee@123");
        this.employee2 = api.login("employee2", "Employee@123");
        this.supervisor1 = api.login("supervisor1", "Supervisor@123");
        this.supervisor2 = api.login("supervisor2", "Supervisor@123");
    }

    List<CaseResult> runAll() {
        List<CaseResult> results = new ArrayList<>();
        results.add(incompleteRequest());
        results.add(incorrectRequest());
        results.add(unauthorizedApproval());
        results.add(otherEmployeesRequest());
        results.add(selfApproval());
        results.add(rejectedRequest());
        results.add(editedRequest());
        results.add(duplicateExecution());
        results.add(agentCannotBypassApproval());
        results.add(unauthenticatedAccess());
        return results;
    }

    private CaseResult incompleteRequest() {
        JsonNode draft = create(employee1, card("", ""));
        Response submit = api.post("/api/requests/" + draft.path("id").asString() + "/submit", employee1, version(draft));
        List<String> fields = fields(submit.body().path("errors"));
        boolean passed = !draft.path("validation").path("valid").asBoolean(true) && submit.status() == 422
                && fields.contains("payload.customerReference") && fields.contains("payload.cardLast4");
        return new CaseResult("W01", "Incomplete service request", "Submitting a draft with missing fields is refused",
                passed, "422 listing customerReference and cardLast4", submit.status() + " " + fields);
    }

    private CaseResult incorrectRequest() {
        Map<String, Object> payload = Map.of("category", "ACCOUNT_SERVICE", "customerReference", "CUS-200111",
                "accountLast4", "7781", "accountType", "SAVINGS", "serviceType", "CHEQUE_BOOK");
        JsonNode draft = create(employee1, payload);
        Response submit = api.post("/api/requests/" + draft.path("id").asString() + "/submit", employee1, version(draft));
        List<String> fields = fields(submit.body().path("errors"));
        boolean passed = submit.status() == 422 && fields.contains("payload.accountType");
        return new CaseResult("W02", "Incorrect service request", "A cheque book on a savings account breaks a business rule",
                passed, "422 on payload.accountType", submit.status() + " " + fields);
    }

    private CaseResult unauthorizedApproval() {
        JsonNode pending = submitted(employee1, card("CUS-104233", "4821"));
        Response byEmployee = api.post("/api/approvals/" + pending.path("id").asString() + "/approve", employee2, version(pending));
        Response byOwner = api.post("/api/approvals/" + pending.path("id").asString() + "/approve", employee1, version(pending));
        boolean passed = byEmployee.status() == 403 && byOwner.status() == 403;
        return new CaseResult("W03", "Unauthorized approval", "Employees cannot approve, including their own request",
                passed, "403 and 403", byEmployee.status() + " and " + byOwner.status());
    }

    private CaseResult otherEmployeesRequest() {
        JsonNode draft = create(employee1, card("CUS-104233", "4821"));
        Response view = api.get("/api/requests/" + draft.path("id").asString(), employee2);
        Response history = api.get("/api/requests/" + draft.path("id").asString() + "/history", employee2);
        boolean passed = view.status() == 404 && history.status() == 404;
        return new CaseResult("W04", "Unauthorized access", "Another employee cannot see a request or its history",
                passed, "404 and 404", view.status() + " and " + history.status());
    }

    private CaseResult selfApproval() {
        JsonNode pending = submitted(supervisor1, card("CUS-300222", "1188"));
        Response approve = api.post("/api/approvals/" + pending.path("id").asString() + "/approve", supervisor1, version(pending));
        return new CaseResult("W05", "Unauthorized approval", "A supervisor cannot approve their own request",
                approve.status() == 403, "403", String.valueOf(approve.status()));
    }

    private CaseResult rejectedRequest() {
        JsonNode pending = submitted(employee1, card("CUS-104233", "4821"));
        String id = pending.path("id").asString();
        Response shortReason = api.post("/api/approvals/" + id + "/reject", supervisor1, Map.of("reason", "no", "version", pending.path("version").asInt()));
        Response reject = api.post("/api/approvals/" + id + "/reject", supervisor1,
                Map.of("reason", "Customer withdrew the request at the counter", "version", pending.path("version").asInt()));
        Response approveAfter = api.post("/api/approvals/" + id + "/approve", supervisor2, Map.of("version", reject.body().path("version").asInt()));
        boolean passed = shortReason.status() == 400 && reject.status() == 200
                && reject.body().path("status").asString().equals("REJECTED") && approveAfter.status() == 409;
        return new CaseResult("W06", "Rejected request", "Rejection needs a reason and is final",
                passed, "400, REJECTED, then 409 on approve",
                shortReason.status() + ", " + reject.body().path("status").asString() + ", then " + approveAfter.status());
    }

    private CaseResult editedRequest() {
        JsonNode pending = submitted(employee2, card("CUS-104233", "4821"));
        String id = pending.path("id").asString();
        Map<String, Object> body = new HashMap<>();
        body.put("title", pending.path("title").asString());
        body.put("description", pending.path("description").asString());
        body.put("payload", card("CUS-104233", "4822"));
        body.put("reason", "Corrected the card digits after checking the ID");
        body.put("version", pending.path("version").asInt());
        Response edit = api.post("/api/approvals/" + id + "/edit-approve", supervisor1, body);
        JsonNode changes = edit.body().path("decision").path("changes");
        boolean passed = edit.status() == 200 && edit.body().path("status").asString().equals("EXECUTED")
                && changes.size() == 1 && changes.get(0).path("field").asString().equals("payload.cardLast4");
        executedRequest = edit.body();
        return new CaseResult("W07", "Edited request", "Edit and approve records the field diff, then executes once",
                passed, "EXECUTED with one change on payload.cardLast4",
                edit.status() + " " + edit.body().path("status").asString() + " " + changes);
    }

    private CaseResult duplicateExecution() {
        String id = executedRequest.path("id").asString();
        String original = executedRequest.path("execution").path("id").asString();
        Response first = api.post("/api/approvals/" + id + "/execute", supervisor2, null);
        Response second = api.post("/api/approvals/" + id + "/execute", supervisor1, null);
        long executions = countActions(api.get("/api/requests/" + id + "/history", supervisor1).body(), "EXECUTED");
        boolean passed = first.body().path("alreadyExecuted").asBoolean() && second.body().path("alreadyExecuted").asBoolean()
                && second.body().path("execution").path("id").asString().equals(original) && executions == 1;
        return new CaseResult("W08", "Duplicate execution", "Repeated execute calls return the original record",
                passed, "alreadyExecuted twice, same record, one EXECUTED entry",
                "alreadyExecuted=" + second.body().path("alreadyExecuted").asBoolean() + ", same record="
                        + second.body().path("execution").path("id").asString().equals(original) + ", EXECUTED entries=" + executions);
    }

    private CaseResult agentCannotBypassApproval() {
        Response ask = api.post("/api/copilot/ask", employee1,
                Map.of("question", "Customer CUS-104233 lost their debit card ending 4821. How do I replace it?"));
        Response prepared = api.post("/api/copilot/prepare-request", employee1, Map.of("runId", ask.body().path("runId").asString()));
        String requestId = prepared.body().path("request").path("id").asString();
        boolean inQueue = false;
        for (JsonNode item : api.get("/api/approvals/queue", supervisor1).body()) {
            inQueue |= item.path("id").asString().equals(requestId);
        }
        boolean passed = prepared.status() == 200 && prepared.body().path("request").path("status").asString().equals("DRAFT") && !inQueue;
        return new CaseResult("W09", "Agents and the approval gate", "The Request Preparation Agent only creates a DRAFT",
                passed, "DRAFT, not in the approval queue",
                prepared.status() + " " + prepared.body().path("request").path("status").asString() + ", in queue=" + inQueue);
    }

    private CaseResult unauthenticatedAccess() {
        Response noToken = api.post("/api/copilot/ask", null, Map.of("question", "What is the card replacement fee?"));
        Response employeeAudit = api.get("/api/audit/runs", employee1);
        boolean passed = noToken.status() == 401 && employeeAudit.status() == 403;
        return new CaseResult("W10", "Unauthorized access", "No token is 401; employees cannot read the audit trail",
                passed, "401 and 403", noToken.status() + " and " + employeeAudit.status());
    }

    private JsonNode submitted(String token, Map<String, Object> payload) {
        JsonNode draft = create(token, payload);
        return api.post("/api/requests/" + draft.path("id").asString() + "/submit", token, version(draft)).body();
    }

    private JsonNode create(String token, Map<String, Object> payload) {
        Map<String, Object> body = Map.of("category", payload.get("category"), "title", "Evaluation request",
                "description", "Created by the evaluation suite on " + LocalDate.now(), "payload", payload);
        return api.post("/api/requests", token, body).body();
    }

    private static Map<String, Object> card(String customer, String last4) {
        return Map.of("category", "CARD_SERVICE", "customerReference", customer, "cardLast4", last4,
                "issueType", "REPLACEMENT", "deliveryBranch", "CAI-012");
    }

    private static Map<String, Object> version(JsonNode request) {
        return Map.of("version", request.path("version").asInt());
    }

    private static List<String> fields(JsonNode errors) {
        List<String> fields = new ArrayList<>();
        errors.forEach(error -> fields.add(error.path("field").asString()));
        return fields;
    }

    private static long countActions(JsonNode history, String action) {
        long count = 0;
        for (JsonNode entry : history) {
            if (entry.path("action").asString().equals(action)) {
                count++;
            }
        }
        return count;
    }
}
