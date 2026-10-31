package com.banquemisr.copilot.assistant;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CitationGuard {

    public static final String NOT_ANSWERED = "None of the approved documents contain enough information to answer this question.";
    public static final String NOT_LINKED = "The answer could not be linked to the approved documents, so it was withheld.";
    public static final String UNKNOWN_SOURCE = "The answer cited a source that was not retrieved, so it was withheld.";

    private static final Pattern MARKER = Pattern.compile("\\[\\s*(E\\d+(?:\\s*,\\s*E\\d+)*)\\s*]", Pattern.CASE_INSENSITIVE);

    public record Checked(String answer, List<String> citedIds, String refusalReason) {

        public boolean refused() {
            return refusalReason != null;
        }

        static Checked refusal(String reason) {
            return new Checked(null, List.of(), reason);
        }
    }

    private CitationGuard() {
    }

    public static Checked check(AnswerDraft draft, Set<String> evidenceIds) {
        if (draft == null || draft.refused() || draft.answer() == null || draft.answer().isBlank()) {
            String reason = draft != null && draft.refusalReason() != null && !draft.refusalReason().isBlank()
                    ? draft.refusalReason() : NOT_ANSWERED;
            return Checked.refusal(reason);
        }

        List<String> cited = citedInText(draft.answer());
        if (cited.isEmpty()) {
            return Checked.refusal(NOT_LINKED);
        }
        List<String> listed = draft.citedEvidence() == null ? List.of() : draft.citedEvidence().stream()
                .map(id -> id.trim().toUpperCase(Locale.ROOT))
                .toList();
        if (!evidenceIds.containsAll(cited) || !evidenceIds.containsAll(listed)) {
            return Checked.refusal(UNKNOWN_SOURCE);
        }
        return new Checked(renumber(draft.answer(), cited), cited, null);
    }

    private static List<String> citedInText(String answer) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = MARKER.matcher(answer);
        while (matcher.find()) {
            for (String id : matcher.group(1).split(",")) {
                ids.add(id.trim().toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(ids);
    }

    private static String renumber(String answer, List<String> cited) {
        Matcher matcher = MARKER.matcher(answer);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            StringBuilder markers = new StringBuilder();
            for (String id : matcher.group(1).split(",")) {
                markers.append('[').append(cited.indexOf(id.trim().toUpperCase(Locale.ROOT)) + 1).append(']');
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(markers.toString()));
        }
        matcher.appendTail(result);
        return result.toString().trim();
    }
}
