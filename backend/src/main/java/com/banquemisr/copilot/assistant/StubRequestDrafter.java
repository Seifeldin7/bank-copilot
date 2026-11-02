package com.banquemisr.copilot.assistant;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class StubRequestDrafter {

    private static final Pattern CATEGORY = Pattern.compile("<category>(.*?)</category>");
    private static final Pattern QUESTION = Pattern.compile("<question>(.*?)</question>", Pattern.DOTALL);
    private static final Pattern BRANCH = Pattern.compile("Branch code of the employee: (\\S+)");
    private static final Pattern CUSTOMER = Pattern.compile("CUS-\\d{6}", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST_4 = Pattern.compile("\\b\\d{4}\\b");
    private static final Pattern MOBILE = Pattern.compile("\\b01[0125]\\d{8}\\b");

    Map<String, Object> draft(String userPrompt) {
        String category = find(CATEGORY, userPrompt);
        String question = find(QUESTION, userPrompt).trim();
        String branch = find(BRANCH, userPrompt);
        String text = question.toLowerCase(Locale.ROOT);
        String customer = find(CUSTOMER, question).toUpperCase(Locale.ROOT);
        String last4 = find(LAST_4, question);

        Map<String, Object> payload = switch (category) {
            case "CARD_SERVICE" -> map(
                    "customerReference", customer,
                    "cardLast4", last4,
                    "issueType", text.contains("stolen") ? "STOLEN" : text.contains("lost") ? "LOST"
                            : text.contains("limit") ? "LIMIT_INQUIRY" : "REPLACEMENT",
                    "deliveryBranch", branch,
                    "notes", "");
            case "DIGITAL_BANKING" -> map(
                    "customerReference", customer,
                    "channel", text.contains("internet") ? "INTERNET_BANKING" : "MOBILE_APP",
                    "issueType", digitalIssue(text),
                    "registeredMobile", find(MOBILE, question),
                    "notes", "");
            case "ACCOUNT_SERVICE" -> map(
                    "customerReference", customer,
                    "accountLast4", last4,
                    "accountType", text.contains("saving") ? "SAVINGS"
                            : text.contains("dollar") || text.contains("foreign") || text.contains("usd") ? "FOREIGN_CURRENCY" : "CURRENT",
                    "serviceType", text.contains("certificate") ? "BALANCE_CERTIFICATE"
                            : text.contains("dormant") || text.contains("reactivat") ? "DORMANCY_REACTIVATION"
                            : text.contains("cheque") ? "CHEQUE_BOOK" : "STATEMENT",
                    "periodFrom", "",
                    "periodTo", "",
                    "notes", "");
            default -> map(
                    "customerReference", customer,
                    "relatedReference", "",
                    "requiredDocuments", documents(text),
                    "dueDate", LocalDate.now().plusDays(14).toString(),
                    "notes", "");
        };
        return map(
                "title", question.replaceAll("\\?$", ""),
                "description", "Prepared by the copilot from the employee's question: " + question,
                "payload", payload);
    }

    private static String digitalIssue(String text) {
        if (text.contains("enrol")) {
            return "ENROLLMENT";
        }
        if (text.contains("device") || text.contains("phone")) {
            return text.contains("internet") ? "ENROLLMENT" : "DEVICE_CHANGE";
        }
        if (text.contains("password") || text.contains("lock")) {
            return "PASSWORD_RESET";
        }
        return "TRANSACTION_ISSUE";
    }

    private static List<String> documents(String text) {
        List<String> documents = new ArrayList<>();
        if (text.contains("address")) {
            documents.add("Proof of address issued within 3 months");
        }
        if (text.contains("income")) {
            documents.add("Proof of income or occupation");
        }
        if (text.contains(" id") || text.contains("passport")) {
            documents.add("Valid national ID or passport");
        }
        return documents;
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
    }
}
