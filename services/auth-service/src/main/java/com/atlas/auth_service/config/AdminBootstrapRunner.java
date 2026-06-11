package com.atlas.auth_service.config;

import com.atlas.auth_service.entity.UserRole;
import com.atlas.auth_service.registration.dto.AdminRegisterRequestDto;
import com.atlas.auth_service.registration.service.IUserRegistrationService;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AtlasUserRespsitory atlasUserRespsitory;
    private final IUserRegistrationService userRegistrationService;

    @Value("${DEFAULT_ADMIN_FIRST_NAME:}")
    private String firstName;

    @Value("${DEFAULT_ADMIN_MIDDLE_NAME:}")
    private String middleName;

    @Value("${DEFAULT_ADMIN_LAST_NAME:}")
    private String lastName;

    @Value("${DEFAULT_ADMIN_USERNAME:}")
    private String username;

    @Value("${DEFAULT_ADMIN_EMAIL:}")
    private String email;

    @Value("${DEFAULT_ADMIN_PASSWORD:}")
    private String password;

    public AdminBootstrapRunner(AtlasUserRespsitory atlasUserRespsitory, IUserRegistrationService userRegistrationService) {
        this.atlasUserRespsitory = atlasUserRespsitory;
        this.userRegistrationService = userRegistrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (atlasUserRespsitory.existsByRole(UserRole.ROLE_ADMIN)) {
            return;
        }

        if (!hasBootstrapData()) {
            return;
        }

        userRegistrationService.registerAdminUser(new AdminRegisterRequestDto(
                firstName,
                StringUtils.hasText(middleName) ? middleName : null,
                lastName,
                username,
                email,
                password,
                password
        ));
    }

    private boolean hasBootstrapData() {
        return StringUtils.hasText(firstName)
                && StringUtils.hasText(lastName)
                && StringUtils.hasText(username)
                && StringUtils.hasText(email)
                && StringUtils.hasText(password);
    }
}
