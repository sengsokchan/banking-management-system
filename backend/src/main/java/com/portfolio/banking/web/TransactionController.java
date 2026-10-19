package com.portfolio.banking.web;

import com.portfolio.banking.domain.Enums;
import com.portfolio.banking.service.TransactionService;
import com.portfolio.banking.web.dto.ApiDtos.DecisionRequest;
import com.portfolio.banking.web.dto.ApiDtos.MoneyMovementRequest;
import com.portfolio.banking.web.dto.ApiDtos.TransactionResponse;
import com.portfolio.banking.web.dto.ApiDtos.TransferRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestParam(required = false) Enums.TransactionStatus status,
            @RequestParam(required = false) Enums.TransactionType type,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return transactionService.search(status, type, accountId, page, size);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return transactionService.get(id);
    }

    @PostMapping("/deposit")
    public TransactionResponse deposit(
            @Valid @RequestBody MoneyMovementRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return transactionService.deposit(request, idempotencyKey);
    }

    @PostMapping("/withdraw")
    public TransactionResponse withdraw(
            @Valid @RequestBody MoneyMovementRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return transactionService.withdraw(request, idempotencyKey);
    }

    @PostMapping("/transfer")
    public TransactionResponse transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return transactionService.transfer(request, idempotencyKey);
    }

    @PostMapping("/{id}/approve")
    public TransactionResponse approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return transactionService.approve(id, request == null ? null : request.comment());
    }

    @PostMapping("/{id}/reject")
    public TransactionResponse reject(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return transactionService.reject(id, request == null ? null : request.comment());
    }
}
