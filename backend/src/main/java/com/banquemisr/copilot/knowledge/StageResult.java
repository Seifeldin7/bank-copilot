package com.banquemisr.copilot.knowledge;

public record StageResult(Stage stage, Status status, long durationMs, String detail) {

    public enum Stage { FINGERPRINT, EXTRACT, CLEAN, CHUNK, SCREEN, EMBED, INDEX }

    public enum Status { SUCCEEDED, SKIPPED, FAILED }

    public static StageResult succeeded(Stage stage, long startedNanos, String detail) {
        return new StageResult(stage, Status.SUCCEEDED, (System.nanoTime() - startedNanos) / 1_000_000, detail);
    }

    public static StageResult skipped(Stage stage, String detail) {
        return new StageResult(stage, Status.SKIPPED, 0, detail);
    }
}
