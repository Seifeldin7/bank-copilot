package com.banquemisr.copilot.identity;

import com.banquemisr.copilot.identity.UserDtos.AuthResponse;
import com.banquemisr.copilot.identity.UserDtos.LoginRequest;
import com.banquemisr.copilot.identity.UserDtos.UserSummary;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final CurrentUser currentUser;

    public AuthController(UserService userService, JwtService jwtService, CurrentUser currentUser) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = userService.authenticate(request.username(), request.password());
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), UserSummary.of(user));
    }

    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal Jwt jwt) {
        return UserSummary.of(currentUser.from(jwt));
    }
}
