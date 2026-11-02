package com.banquemisr.copilot.assistant;

public final class JsonReplies {

    private JsonReplies() {
    }

    public static String extractObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("The model reply contains no JSON object");
        }
        return text.substring(start, end + 1);
    }
}
