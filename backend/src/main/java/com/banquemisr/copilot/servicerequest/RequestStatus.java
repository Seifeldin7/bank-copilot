package com.banquemisr.copilot.servicerequest;

import java.util.Set;

public enum RequestStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    EXECUTED;

    public boolean canMoveTo(RequestStatus next) {
        return switch (this) {
            case DRAFT -> next == PENDING_APPROVAL;
            case PENDING_APPROVAL -> Set.of(APPROVED, REJECTED).contains(next);
            case APPROVED -> next == EXECUTED;
            case REJECTED, EXECUTED -> false;
        };
    }
}
