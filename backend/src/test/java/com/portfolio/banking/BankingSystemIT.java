package com.portfolio.banking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.banking.domain.StaffUser;
import com.portfolio.banking.repo.AccountRepository;
import com.portfolio.banking.repo.StaffUserRepository;
import com.portfolio.banking.service.TransactionService;
import com.portfolio.banking.web.dto.ApiDtos.MoneyMovementRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankingSystemIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Test
    void rejectsMissingAndUnauthorizedCalls() throws Exception {
        mvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String auditor = login("auditor", "ChangeMe-Auditor-1");
        mvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + auditor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "sourceAccountId", UUID.randomUUID(),
                                "destinationAccountId", UUID.randomUUID(),
                                "amount", "10.00"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + login("teller", "ChangeMe-Teller-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void storesPasswordsAsHashesAndFindsCustomersByNameOrNumber() throws Exception {
        StaffUser teller = staffUserRepository.findByUsername("teller").orElseThrow();
        assertThat(teller.getPasswordHash()).doesNotContain("ChangeMe-Teller-1");
        assertThat(teller.getPasswordHash()).startsWith("$2");

        String token = login("teller", "ChangeMe-Teller-1");
        JsonNode created = body(mvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(customer("Search", "Person"))))
                .andExpect(status().isCreated())
                .andReturn());

        mvc.perform(get("/api/customers").param("query", "Search Person").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerNumber").value(created.get("customerNumber").asText()));

        mvc.perform(get("/api/customers").param("query", created.get("customerNumber").asText()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(created.get("id").asText()));
    }

    @Test
    void movesSimulatedFundsAtomicallyAndBlocksInvalidTransfers() throws Exception {
        String teller = login("teller", "ChangeMe-Teller-1");
        String source = openAccount(teller, "400.00");
        String destination = openAccount(teller, "25.00");

        JsonNode transfer = body(mvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + teller)
                        .header("Idempotency-Key", "transfer-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "sourceAccountId", source,
                                "destinationAccountId", destination,
                                "amount", "150.50",
                                "description", "Simulated invoice"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn());

        assertBalance(teller, source, "249.50");
        assertBalance(teller, destination, "175.50");

        JsonNode replay = body(mvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + teller)
                        .header("Idempotency-Key", "transfer-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "sourceAccountId", source,
                                "destinationAccountId", destination,
                                "amount", "150.50",
                                "description", "Simulated invoice"
                        ))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(replay.get("id").asText()).isEqualTo(transfer.get("id").asText());
        assertBalance(teller, source, "249.50");

        mvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + teller)
                        .header("Idempotency-Key", "transfer-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "sourceAccountId", source,
                                "destinationAccountId", destination,
                                "amount", "10.00"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertBalance(teller, source, "249.50");

        mvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + teller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("accountId", source, "amount", "1000.00"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertBalance(teller, source, "249.50");

        mvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + teller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "sourceAccountId", source,
                                "destinationAccountId", source,
                                "amount", "1.00"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_TRANSFER"));

        mvc.perform(post("/api/accounts/" + destination + "/freeze").header("Authorization", "Bearer " + teller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        mvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + teller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "sourceAccountId", source,
                                "destinationAccountId", destination,
                                "amount", "1.00"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
        assertBalance(teller, source, "249.50");
        assertBalance(teller, destination, "175.50");

        String auditor = login("auditor", "ChangeMe-Auditor-1");
        mvc.perform(get("/api/audit-logs").param("query", "ACCOUNT_FROZEN").header("Authorization", "Bearer " + auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].success").value(true));
        mvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + teller))
                .andExpect(status().isForbidden());
    }

    @Test
    void largeTransferWaitsForADifferentApprover() throws Exception {
        String teller = login("teller", "ChangeMe-Teller-1");
        String admin = login("admin", "ChangeMe-Admin-1");
        String source = openAccount(teller, "20000.00");
        String destination = openAccount(teller, "0.00");

        JsonNode pending = body(mvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + teller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "sourceAccountId", source,
                                "destinationAccountId", destination,
                                "amount", "10000.00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn());
        assertBalance(teller, source, "20000.00");
        assertBalance(teller, destination, "0.00");

        mvc.perform(post("/api/transactions/" + pending.get("id").asText() + "/approve")
                        .header("Authorization", "Bearer " + teller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"no\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/transactions/" + pending.get("id").asText() + "/approve")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertBalance(admin, source, "10000.00");
        assertBalance(admin, destination, "10000.00");

        JsonNode own = body(mvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "sourceAccountId", source,
                                "destinationAccountId", destination,
                                "amount", "10000.00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn());
        mvc.perform(post("/api/transactions/" + own.get("id").asText() + "/approve")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL"));
        assertBalance(admin, source, "10000.00");
    }

    @Test
    void closesOnlyZeroAccountsAndThenDeactivatesTheCustomer() throws Exception {
        String teller = login("teller", "ChangeMe-Teller-1");
        JsonNode customer = body(mvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + teller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(customer("Close", "Ready"))))
                .andExpect(status().isCreated())
                .andReturn());
        JsonNode account = body(mvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + teller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "customerId", customer.get("id").asText(),
                                "accountType", "CHECKING",
                                "initialDeposit", "30.00"
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        mvc.perform(post("/api/accounts/" + account.get("id").asText() + "/close").header("Authorization", "Bearer " + teller))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ZERO"));
        mvc.perform(post("/api/customers/" + customer.get("id").asText() + "/deactivate").header("Authorization", "Bearer " + teller))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CUSTOMER_HAS_OPEN_ACCOUNTS"));

        mvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + teller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("accountId", account.get("id").asText(), "amount", "30.00"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/accounts/" + account.get("id").asText() + "/close").header("Authorization", "Bearer " + teller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(post("/api/customers/" + customer.get("id").asText() + "/deactivate").header("Authorization", "Bearer " + teller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void onlyOneConcurrentWithdrawalCanSpendTheBalance() throws Exception {
        String teller = login("teller", "ChangeMe-Teller-1");
        String accountId = openAccount(teller, "100.00");
        var principal = userDetailsService.loadUserByUsername("teller");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Future<?> first = pool.submit(() -> withdrawConcurrently(principal, accountId, start, completed, rejected));
        Future<?> second = pool.submit(() -> withdrawConcurrently(principal, accountId, start, completed, rejected));
        start.countDown();
        first.get();
        second.get();
        pool.shutdown();

        assertThat(completed.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(accountRepository.findById(UUID.fromString(accountId)).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void adminCanUpdateTheApprovalThreshold() throws Exception {
        String admin = login("admin", "ChangeMe-Admin-1");
        mvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "bankDisplayName", "SC Peace SK Bank",
                                "approvalThreshold", "10000.00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalThreshold").value(10000.00));

        mvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + login("teller", "ChangeMe-Teller-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "bankDisplayName", "SC Peace SK Bank",
                                "approvalThreshold", "50.00"
                        ))))
                .andExpect(status().isForbidden());
    }

    private void withdrawConcurrently(org.springframework.security.core.userdetails.UserDetails principal, String accountId, CountDownLatch start, AtomicInteger completed, AtomicInteger rejected) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        try {
            start.await();
            transactionService.withdraw(new MoneyMovementRequest(UUID.fromString(accountId), new BigDecimal("100.00"), "Race", null), null);
            completed.incrementAndGet();
        } catch (RuntimeException ex) {
            rejected.incrementAndGet();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            rejected.incrementAndGet();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String openAccount(String token, String deposit) throws Exception {
        JsonNode customer = body(mvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(customer("Ledger", "Owner"))))
                .andExpect(status().isCreated())
                .andReturn());
        JsonNode account = body(mvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "customerId", customer.get("id").asText(),
                                "accountType", "CHECKING",
                                "initialDeposit", deposit
                        ))))
                .andExpect(status().isCreated())
                .andReturn());
        return account.get("id").asText();
    }

    private void assertBalance(String token, String accountId, String expected) throws Exception {
        JsonNode account = body(mvc.perform(get("/api/accounts/" + accountId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(new BigDecimal(account.get("balance").asText())).isEqualByComparingTo(expected);
    }

    private Map<String, Object> customer(String first, String last) {
        return Map.of(
                "firstName", first,
                "lastName", last + "-" + UUID.randomUUID().toString().substring(0, 8),
                "email", UUID.randomUUID() + "@example.com",
                "phone", "555-0100",
                "dateOfBirth", LocalDate.of(1992, 5, 4).toString(),
                "addressLine", "1 Simulated Street",
                "city", "Harbor City",
                "country", "Exampleland"
        );
    }

    private String login(String username, String password) throws Exception {
        JsonNode response = body(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn());
        return response.get("accessToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
