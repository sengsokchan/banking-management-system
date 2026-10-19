package com.portfolio.banking.security;

import com.portfolio.banking.web.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentActor {

    private CurrentActor() {
    }

    public static StaffPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof StaffPrincipal principal)) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Authentication is required.");
        }
        return principal;
    }
}
