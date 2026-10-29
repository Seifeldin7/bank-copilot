package com.banquemisr.copilot.identity;

import com.banquemisr.copilot.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public AppUser from(Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getClaimAsString("uid")))
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "The access token is invalid.", List.of()));
    }
}
