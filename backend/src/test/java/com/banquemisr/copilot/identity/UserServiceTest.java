package com.banquemisr.copilot.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banquemisr.copilot.audit.AuditService;
import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.UserDtos.CreateUserRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    @SuppressWarnings("deprecation")
    private final PasswordEncoder encoder = NoOpPasswordEncoder.getInstance();
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserService service = new UserService(users, encoder, audit);
    private final AppUser supervisor = new AppUser("supervisor1", "x", "Nadia Fawzy", Role.SUPERVISOR, "CAI-012");

    @Test
    void provisionedUsersAlwaysGetTheEmployeeRole() {
        when(users.existsByUsername("new.teller")).thenReturn(false);

        AppUser created = service.createEmployee(new CreateUserRequest(" New.Teller ", "New Teller", "CAI-012", "Welcome12345"), supervisor);

        assertThat(created.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(created.getUsername()).isEqualTo("new.teller");
        verify(users).save(created);
        verify(audit).record(eq("USER_PROVISIONED"), eq(supervisor), eq("User"), eq("new.teller"), anyString());
    }

    @Test
    void rejectsADuplicateUsername() {
        when(users.existsByUsername("employee1")).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployee(new CreateUserRequest("employee1", "Someone", "CAI-012", "Welcome12345"), supervisor))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void authenticatesWithTheRightPasswordAndRecordsTheLogin() {
        AppUser employee = new AppUser("employee1", "Employee@123", "Mariam Adel", Role.EMPLOYEE, "CAI-012");
        when(users.findByUsername("employee1")).thenReturn(Optional.of(employee));

        assertThat(service.authenticate("Employee1", "Employee@123")).isSameAs(employee);
        verify(audit).record(eq("LOGIN"), eq(employee), any(), any(), any());
    }

    @Test
    void rejectsAWrongPasswordAndAnUnknownUserTheSameWay() {
        AppUser employee = new AppUser("employee1", "Employee@123", "Mariam Adel", Role.EMPLOYEE, "CAI-012");
        when(users.findByUsername("employee1")).thenReturn(Optional.of(employee));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertUnauthorized(() -> service.authenticate("employee1", "wrong"));
        assertUnauthorized(() -> service.authenticate("ghost", "Employee@123"));
    }

    private void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessage("Invalid username or password.");
    }
}
