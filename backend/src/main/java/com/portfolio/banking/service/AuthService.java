package com.portfolio.banking.service;

import com.portfolio.banking.config.AppProperties;
import com.portfolio.banking.domain.RefreshToken;
import com.portfolio.banking.domain.StaffUser;
import com.portfolio.banking.repo.RefreshTokenRepository;
import com.portfolio.banking.repo.StaffUserRepository;
import com.portfolio.banking.security.CurrentActor;
import com.portfolio.banking.security.JwtService;
import com.portfolio.banking.security.StaffPrincipal;
import com.portfolio.banking.security.StaffUserDetailsService;
import com.portfolio.banking.web.ApiException;
import com.portfolio.banking.web.dto.ApiDtos.AuthResponse;
import com.portfolio.banking.web.dto.ApiDtos.ChangePasswordRequest;
import com.portfolio.banking.web.dto.ApiDtos.LoginRequest;
import com.portfolio.banking.web.dto.ApiDtos.UserProfile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthService {

    private final StaffUserRepository staffUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StaffUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyHash;

    public AuthService(
            StaffUserRepository staffUserRepository,
            RefreshTokenRepository refreshTokenRepository,
            StaffUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService,
            LoginAttemptService loginAttemptService,
            AppProperties appProperties
    ) {
        this.staffUserRepository = staffUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
        this.appProperties = appProperties;
        this.dummyHash = passwordEncoder.encode("dummy-password-not-used");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        StaffUser user = staffUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            auditService.failureAs(username, "LOGIN_FAILURE", "USER", null, "Sign-in failed.");
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password.");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            auditService.failureAs(user.getUsername(), "LOGIN_FAILURE", "USER", user.getId().toString(), "Sign-in blocked because the account is locked.");
            throw ApiException.unauthorized("ACCOUNT_LOCKED", "This account is temporarily locked. Try again later.");
        }
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(user.getId());
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password.");
        }
        user.setFailedAttemptCount(0);
        user.setLockedUntil(null);
        StaffPrincipal principal = (StaffPrincipal) userDetailsService.loadUserByUsername(user.getUsername());
        auditService.success("LOGIN_SUCCESS", "USER", user.getId().toString(), "Signed in.");
        return issue(principal);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(Support.sha256(refreshToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH", "The refresh token is invalid."));
        if (stored.isRevoked()) {
            refreshTokenRepository.revokeActiveForUser(stored.getUser().getId());
            auditService.failureAs(stored.getUser().getUsername(), "REFRESH_REUSED", "USER", stored.getUser().getId().toString(), "A revoked refresh token was presented.");
            throw ApiException.unauthorized("REFRESH_REUSED", "This refresh token is no longer valid. Sign in again.");
        }
        if (stored.getExpiresAt().isBefore(Instant.now()) || !stored.getUser().isActive()) {
            stored.setRevoked(true);
            throw ApiException.unauthorized("INVALID_REFRESH", "The refresh token is invalid.");
        }
        stored.setRevoked(true);
        StaffPrincipal principal = userDetailsService.loadActiveById(stored.getUser().getId());
        return issue(principal);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(Support.sha256(refreshToken.trim())).ifPresent(stored -> {
            stored.setRevoked(true);
            auditService.recordInCurrentTransaction(
                    stored.getUser().getId(),
                    stored.getUser().getUsername(),
                    "LOGOUT",
                    "USER",
                    stored.getUser().getId().toString(),
                    true,
                    "Signed out."
            );
        });
    }

    @Transactional(readOnly = true)
    public UserProfile me() {
        return UserProfile.from(CurrentActor.require());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Support.checkPassword(request.newPassword());
        StaffPrincipal actor = CurrentActor.require();
        StaffUser user = staffUserRepository.findById(actor.getId())
                .orElseThrow(() -> ApiException.notFound("Employee was not found."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.failure("PASSWORD_CHANGE_FAILED", "USER", user.getId().toString(), "The current password is incorrect.");
            throw ApiException.unauthorized("CURRENT_PASSWORD_INVALID", "The current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeActiveForUser(user.getId());
        auditService.success("PASSWORD_CHANGED", "USER", user.getId().toString(), "Password changed.");
    }

    private AuthResponse issue(StaffPrincipal principal) {
        String rawRefresh = newRefreshToken();
        RefreshToken token = new RefreshToken();
        token.setUser(staffUserRepository.getReferenceById(principal.getId()));
        token.setTokenHash(Support.sha256(rawRefresh));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(appProperties.getJwt().getRefreshTokenDays())));
        token.setRevoked(false);
        refreshTokenRepository.save(token);
        return new AuthResponse(
                jwtService.createAccessToken(principal),
                rawRefresh,
                jwtService.accessTokenSeconds(),
                UserProfile.from(principal)
        );
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
