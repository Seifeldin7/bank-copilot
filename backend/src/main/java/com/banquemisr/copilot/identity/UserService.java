package com.banquemisr.copilot.identity;

import com.banquemisr.copilot.audit.AuditService;
import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.UserDtos.CreateUserRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final String unknownUserHash;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.unknownUserHash = passwordEncoder.encode("unknown-user-placeholder");
    }

    @Transactional
    public AppUser authenticate(String username, String password) {
        Optional<AppUser> user = users.findByUsername(username.trim().toLowerCase());
        String hash = user.map(AppUser::getPasswordHash).orElse(unknownUserHash);
        boolean matches = passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !matches || !user.get().isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password.", List.of());
        }
        audit.record("LOGIN", user.get(), "User", user.get().getUsername(), "Successful login");
        return user.get();
    }

    public List<AppUser> list() {
        return users.findAllByOrderByFullNameAsc();
    }

    @Transactional
    public AppUser createEmployee(CreateUserRequest request, AppUser createdBy) {
        String username = request.username().trim().toLowerCase();
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("The username \"" + username + "\" is already taken.");
        }
        AppUser user = new AppUser(username, passwordEncoder.encode(request.password()), request.fullName().trim(),
                Role.EMPLOYEE, request.branchCode());
        users.save(user);
        audit.record("USER_PROVISIONED", createdBy, "User", username, "Employee account created with role EMPLOYEE");
        return user;
    }
}
