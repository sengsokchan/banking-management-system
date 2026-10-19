package com.portfolio.banking.web;

import com.portfolio.banking.service.UserAdminService;
import com.portfolio.banking.web.dto.ApiDtos.PasswordResetRequest;
import com.portfolio.banking.web.dto.ApiDtos.RoleResponse;
import com.portfolio.banking.web.dto.ApiDtos.UserResponse;
import com.portfolio.banking.web.dto.ApiDtos.UserUpdateRequest;
import com.portfolio.banking.web.dto.ApiDtos.UserWriteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserAdminService userAdminService;

    public UserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/users")
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return userAdminService.list(page, size);
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        return userAdminService.roles();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserWriteRequest request) {
        return userAdminService.create(request);
    }

    @PutMapping("/users/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
        return userAdminService.update(id, request);
    }

    @PostMapping("/users/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordResetRequest request) {
        userAdminService.resetPassword(id, request);
    }
}
