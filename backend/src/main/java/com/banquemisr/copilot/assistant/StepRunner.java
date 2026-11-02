package com.banquemisr.copilot.assistant;

import com.banquemisr.copilot.assistant.CopilotDtos.StepStatus;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class StepRunner {

    public static class StepFailure extends RuntimeException {

        private final StepStatus status;

        public StepFailure(StepStatus status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        public StepStatus status() {
            return status;
        }
    }

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public <T> T run(Supplier<T> work, Duration timeout) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                return work.get();
            } finally {
                MDC.clear();
            }
        }, executor);

        try {
            return future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof StepFailure failure) {
                throw failure;
            }
            if (cause instanceof TimeoutException) {
                throw new StepFailure(StepStatus.TIMED_OUT, "Step exceeded the " + timeout.toSeconds() + " s timeout", cause);
            }
            throw new StepFailure(StepStatus.FAILED, "Step failed: " + cause.getClass().getSimpleName(), cause);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
