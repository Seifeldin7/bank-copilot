package com.banquemisr.copilot.retrieval;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class QueryTerms {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "are", "was", "were", "with", "what", "which", "who", "whom", "when", "where",
            "why", "how", "does", "did", "can", "could", "should", "would", "will", "shall", "may", "might", "must",
            "have", "has", "had", "this", "that", "these", "those", "there", "their", "they", "them", "you", "your",
            "our", "from", "into", "about", "any", "all", "not", "but", "its", "his", "her", "she", "him", "please",
            "tell", "know", "need", "want", "get", "give", "also", "than", "then", "some", "much", "many", "more",
            "most", "such", "only", "own", "same", "very", "just", "being", "been", "out", "over", "under", "again",
            "here", "each", "both", "few", "other", "let", "like");

    private QueryTerms() {
    }

    public static List<String> of(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .map(QueryTerms::stem)
                .filter(term -> term.length() > 2 && !STOP_WORDS.contains(term))
                .distinct()
                .toList();
    }

    public static boolean matches(String term, String word) {
        return word.equals(term)
                || (term.length() >= 4 && word.startsWith(term))
                || (word.length() >= 4 && term.startsWith(word));
    }

    private static String stem(String word) {
        return word.length() > 4 && word.endsWith("s") && !word.endsWith("ss") ? word.substring(0, word.length() - 1) : word;
    }
}
