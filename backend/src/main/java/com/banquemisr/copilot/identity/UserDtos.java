package com.banquemisr.copilot.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserRef(UUID id, String username, String fullName) {
        public static UserRef of(AppUser user) {
            return user == null ? null : new UserRef(user.getId(), user.getUsername(), user.getFullName());
        }
    }

    public record UserSummary(UUID id, String username, String fullName, Role role, String branchCode,
                              boolean enabled, Instant createdAt) {
        public static UserSummary of(AppUser user) {
            return new UserSummary(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                    user.getBranchCode(), user.isEnabled(), user.getCreatedAt());
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required.") String username,
            @NotBlank(message = "Password is required.") String password) {
    }

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserSummary user) {
    }

    public record CreateUserRequest(
            @NotBlank(message = "Username is required.")
            @Pattern(regexp = "[a-z][a-z0-9._]{2,29}", message = "Use 3-30 lowercase letters, digits, dots or underscores, starting with a letter.")
            String username,
            @NotBlank(message = "Full name is required.")
            @Size(min = 3, max = 120, message = "Full name must be 3 to 120 characters.")
            String fullName,
            @NotBlank(message = "Branch code is required.")
            @Pattern(regexp = "[A-Z]{3}-\\d{3}", message = "Branch code must look like CAI-012.")
            String branchCode,
            @NotBlank(message = "Password is required.")
            @Pattern(regexp = "(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{10,72}", message = "Password needs at least 10 characters with upper case, lower case and a digit.")
            String password) {
    }
}
