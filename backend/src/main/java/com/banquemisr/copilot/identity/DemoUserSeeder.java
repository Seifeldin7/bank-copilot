package com.banquemisr.copilot.identity;

import com.banquemisr.copilot.common.CopilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CopilotProperties properties;

    public DemoUserSeeder(AppUserRepository users, PasswordEncoder passwordEncoder, CopilotProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.demo().seedUsers()) {
            return;
        }
        seed("employee1", "Employee@123", "Mariam Adel", Role.EMPLOYEE, "CAI-012");
        seed("employee2", "Employee@123", "Omar Hassan", Role.EMPLOYEE, "CAI-012");
        seed("supervisor1", "Supervisor@123", "Nadia Fawzy", Role.SUPERVISOR, "CAI-012");
        seed("supervisor2", "Supervisor@123", "Karim Mostafa", Role.SUPERVISOR, "ALX-004");
    }

    private void seed(String username, String password, String fullName, Role role, String branchCode) {
        if (users.existsByUsername(username)) {
            return;
        }
        users.save(new AppUser(username, passwordEncoder.encode(password), fullName, role, branchCode));
        log.info("Seeded demo user {}", username);
    }
}
