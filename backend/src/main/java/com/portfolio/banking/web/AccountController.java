package com.portfolio.banking.web;

import com.portfolio.banking.domain.Enums;
import com.portfolio.banking.service.AccountService;
import com.portfolio.banking.web.dto.ApiDtos.AccountRequest;
import com.portfolio.banking.web.dto.ApiDtos.AccountResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public PageResponse<AccountResponse> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) Enums.AccountStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return accountService.search(customerId, status, query, page, size);
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return accountService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountRequest request) {
        return accountService.create(request);
    }

    @PostMapping("/{id}/freeze")
    public AccountResponse freeze(@PathVariable UUID id) {
        return accountService.freeze(id);
    }

    @PostMapping("/{id}/activate")
    public AccountResponse activate(@PathVariable UUID id) {
        return accountService.activate(id);
    }

    @PostMapping("/{id}/close")
    public AccountResponse close(@PathVariable UUID id) {
        return accountService.close(id);
    }
}
