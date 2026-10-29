package com.banquemisr.copilot.knowledge;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("corpus")
public class CorpusStatus implements HealthIndicator {

    private final DocumentRepository documents;
    private volatile boolean ready;

    public CorpusStatus(DocumentRepository documents) {
        this.documents = documents;
    }

    public void markReady() {
        this.ready = true;
    }

    @Override
    public Health health() {
        long active = documents.countByStatus(Document.Status.ACTIVE);
        Health.Builder builder = ready && active > 0 ? Health.up() : Health.outOfService();
        return builder.withDetail("synced", ready).withDetail("activeDocuments", active).build();
    }
}
