package com.banquemisr.copilot.servicerequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RequestAccessPolicyTest {

    private final AppUser owner = RequestFixtures.employee("employee1");
    private final AppUser colleague = RequestFixtures.employee("employee2");
    private final AppUser supervisor = RequestFixtures.supervisor("supervisor1");
    private final ServiceRequest request = RequestFixtures.pending(owner);

    @Test
    void anotherEmployeeCannotSeeTheRequestAtAll() {
        assertStatus(() -> RequestAccessPolicy.requireVisible(colleague, request), HttpStatus.NOT_FOUND);
        assertStatus(() -> RequestAccessPolicy.requireOwner(colleague, request), HttpStatus.NOT_FOUND);
    }

    @Test
    void supervisorsCanSeeButNotEditSomeoneElsesRequest() {
        assertThatCode(() -> RequestAccessPolicy.requireVisible(supervisor, request)).doesNotThrowAnyException();
        assertStatus(() -> RequestAccessPolicy.requireOwner(supervisor, request), HttpStatus.FORBIDDEN);
    }

    @Test
    void anEmployeeCannotDecide() {
        assertStatus(() -> RequestAccessPolicy.requireCanDecide(owner, request), HttpStatus.FORBIDDEN);
    }

    @Test
    void aSupervisorCannotDecideTheirOwnRequest() {
        ServiceRequest own = RequestFixtures.pending(supervisor);

        assertStatus(() -> RequestAccessPolicy.requireCanDecide(supervisor, own), HttpStatus.FORBIDDEN);
        assertThatCode(() -> RequestAccessPolicy.requireCanDecide(supervisor, request)).doesNotThrowAnyException();
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(status));
    }
}
