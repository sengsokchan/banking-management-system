package com.portfolio.banking.service;

import com.portfolio.banking.domain.Enums;
import com.portfolio.banking.domain.Role;
import com.portfolio.banking.domain.StaffUser;
import com.portfolio.banking.repo.RefreshTokenRepository;
import com.portfolio.banking.repo.RoleRepository;
import com.portfolio.banking.repo.StaffUserRepository;
import com.portfolio.banking.security.CurrentActor;
import com.portfolio.banking.web.ApiException;
import com.portfolio.banking.web.PageResponse;
import com.portfolio.banking.web.dto.ApiDtos.PasswordResetRequest;
import com.portfolio.banking.web.dto.ApiDtos.RoleResponse;
import com.portfolio.banking.web.dto.ApiDtos.UserResponse;
import com.portfolio.banking.web.dto.ApiDtos.UserUpdateRequest;
import com.portfolio.banking.web.dto.ApiDtos.UserWriteRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAdminService {

    private final StaffUserRepository staffUserRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserAdminService(
            StaffUserRepository staffUserRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.staffUserRepository = staffUserRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size) {
        return PageResponse.from(staffUserRepository.findAll(PageRequest.of(page, size, Sort.by("username")))
                .map(UserResponse::from));
    }

    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        return roleRepository.findAll(Sort.by("name")).stream().map(RoleResponse::from).toList();
    }

    @PreAuthorize("hasAuthority('USER_WRITE')")
    @Transactional
    public UserResponse create(UserWriteRequest request) {
        String username = normalizeUsername(request.username());
        String email = request.email().trim().toLowerCase();
        Support.checkPassword(request.password());
        if (staffUserRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("DUPLICATE_USERNAME", "That username is already in use.");
        }
        if (staffUserRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("DUPLICATE_EMAIL", "That email is already in use.");
        }
        StaffUser user = new StaffUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        user.setRoles(resolveRoles(request.roles()));
        staffUserRepository.save(user);
        auditService.success("USER_CREATED", "USER", user.getId().toString(), "Created employee " + user.getUsername() + ".");
        return UserResponse.from(user);
    }

    @PreAuthorize("hasAuthority('USER_WRITE')")
    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {
        StaffUser user = load(id);
        boolean active = Boolean.TRUE.equals(request.active());
        if (!active && user.getId().equals(CurrentActor.require().getId())) {
            throw ApiException.unprocessable("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own employee account.");
        }
        Set<String> roleNames = normalizeRoleNames(request.roles());
        ensureAdminRemains(user, active, roleNames);
        String email = request.email().trim().toLowerCase();
        if (staffUserRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw ApiException.conflict("DUPLICATE_EMAIL", "That email is already in use.");
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setActive(active);
        user.setRoles(resolveRoles(roleNames.stream().toList()));
        if (!active) {
            refreshTokenRepository.revokeActiveForUser(user.getId());
        }
        auditService.success("USER_UPDATED", "USER", user.getId().toString(), "Updated employee " + user.getUsername() + ".");
        return UserResponse.from(user);
    }

    @PreAuthorize("hasAuthority('USER_WRITE')")
    @Transactional
    public void resetPassword(UUID id, PasswordResetRequest request) {
        Support.checkPassword(request.password());
        StaffUser user = load(id);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFailedAttemptCount(0);
        user.setLockedUntil(null);
        refreshTokenRepository.revokeActiveForUser(user.getId());
        auditService.success("PASSWORD_RESET", "USER", user.getId().toString(), "Reset password for " + user.getUsername() + ".");
    }

    private void ensureAdminRemains(StaffUser user, boolean willBeActive, Set<String> roles) {
        boolean currentlyActiveAdmin = user.isActive() && hasRole(user, Enums.RoleName.ADMIN.name());
        boolean nextActiveAdmin = willBeActive && roles.contains(Enums.RoleName.ADMIN.name());
        if (currentlyActiveAdmin && !nextActiveAdmin && staffUserRepository.countActiveAdmins() <= 1) {
            throw ApiException.unprocessable("LAST_ADMIN", "The system must keep at least one active administrator.");
        }
    }

    private boolean hasRole(StaffUser user, String role) {
        return user.getRoles().stream().anyMatch(item -> item.getName().equals(role));
    }

    private Set<Role> resolveRoles(List<String> requested) {
        Set<String> names = normalizeRoleNames(requested);
        Set<Role> roles = new HashSet<>();
        for (String name : names) {
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> ApiException.badRequest("ROLE_NOT_FOUND", "Unknown role: " + name + ".")));
        }
        return roles;
    }

    private Set<String> normalizeRoleNames(List<String> requested) {
        Set<String> names = new HashSet<>();
        for (String role : requested) {
            if (role == null || role.isBlank()) {
                continue;
            }
            names.add(role.trim().toUpperCase());
        }
        if (names.isEmpty()) {
            throw ApiException.badRequest("ROLE_REQUIRED", "Choose at least one role.");
        }
        return names;
    }

    private String normalizeUsername(String username) {
        String normalized = username.trim().toLowerCase();
        if (!normalized.matches("[a-z][a-z0-9._-]{2,31}")) {
            throw ApiException.badRequest(
                    "USERNAME_INVALID",
                    "Username must start with a letter and use 3 to 32 lowercase letters, numbers, dots, underscores, or hyphens."
            );
        }
        return normalized;
    }

    private StaffUser load(UUID id) {
        return staffUserRepository.findWithAccessById(id).orElseThrow(() -> ApiException.notFound("Employee was not found."));
    }
}
