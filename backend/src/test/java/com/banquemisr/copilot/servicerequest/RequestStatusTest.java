package com.banquemisr.copilot.servicerequest;

import static com.banquemisr.copilot.servicerequest.RequestStatus.APPROVED;
import static com.banquemisr.copilot.servicerequest.RequestStatus.DRAFT;
import static com.banquemisr.copilot.servicerequest.RequestStatus.EXECUTED;
import static com.banquemisr.copilot.servicerequest.RequestStatus.PENDING_APPROVAL;
import static com.banquemisr.copilot.servicerequest.RequestStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banquemisr.copilot.common.ApiException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RequestStatusTest {

    private static final Map<RequestStatus, Set<RequestStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(PENDING_APPROVAL),
            PENDING_APPROVAL, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(EXECUTED),
            REJECTED, Set.of(),
            EXECUTED, Set.of());

    @Test
    void onlyTheDocumentedTransitionsAreAllowed() {
        for (RequestStatus from : RequestStatus.values()) {
            for (RequestStatus to : RequestStatus.values()) {
                assertThat(from.canMoveTo(to)).as(from + " -> " + to).isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    void anInvalidTransitionIsAConflictAndLeavesTheStatusUnchanged() {
        ServiceRequest request = RequestFixtures.draft(RequestFixtures.employee("employee1"));

        for (RequestStatus target : EnumSet.of(APPROVED, REJECTED, EXECUTED)) {
            assertThatThrownBy(() -> request.moveTo(target))
                    .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT));
        }
        assertThat(request.getStatus()).isEqualTo(DRAFT);
    }

    @Test
    void submittingAndExecutingStampTheirTimes() {
        ServiceRequest request = RequestFixtures.pending(RequestFixtures.employee("employee1"));
        assertThat(request.getSubmittedAt()).isNotNull();

        request.moveTo(APPROVED);
        request.moveTo(EXECUTED);
        assertThat(request.getExecutedAt()).isNotNull();
    }
}
