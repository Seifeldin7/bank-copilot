package com.banquemisr.copilot.identity;

import com.banquemisr.copilot.identity.UserDtos.CreateUserRequest;
import com.banquemisr.copilot.identity.UserDtos.UserSummary;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('SUPERVISOR')")
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public UserController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<UserSummary> list() {
        return userService.list().stream().map(UserSummary::of).toList();
    }

    @PostMapping
    public UserSummary create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        return UserSummary.of(userService.createEmployee(request, currentUser.from(jwt)));
    }
}
