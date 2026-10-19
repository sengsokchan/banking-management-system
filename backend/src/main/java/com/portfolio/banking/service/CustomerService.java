package com.portfolio.banking.service;

import com.portfolio.banking.domain.Customer;
import com.portfolio.banking.domain.Enums;
import com.portfolio.banking.domain.StaffUser;
import com.portfolio.banking.repo.AccountRepository;
import com.portfolio.banking.repo.CustomerRepository;
import com.portfolio.banking.repo.StaffUserRepository;
import com.portfolio.banking.security.CurrentActor;
import com.portfolio.banking.web.ApiException;
import com.portfolio.banking.web.PageResponse;
import com.portfolio.banking.web.dto.ApiDtos.AccountResponse;
import com.portfolio.banking.web.dto.ApiDtos.CustomerDetailResponse;
import com.portfolio.banking.web.dto.ApiDtos.CustomerRequest;
import com.portfolio.banking.web.dto.ApiDtos.CustomerResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final StaffUserRepository staffUserRepository;
    private final SequenceService sequenceService;
    private final AuditService auditService;
    private final Clock clock;

    public CustomerService(
            CustomerRepository customerRepository,
            AccountRepository accountRepository,
            StaffUserRepository staffUserRepository,
            SequenceService sequenceService,
            AuditService auditService,
            Clock clock
    ) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.staffUserRepository = staffUserRepository;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String query, int page, int size) {
        return PageResponse.from(customerRepository.search(
                Support.contains(query),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "registeredAt"))
        ).map(CustomerResponse::from));
    }

    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Transactional(readOnly = true)
    public CustomerDetailResponse get(UUID id) {
        Customer customer = load(id);
        var accounts = accountRepository.search(id, null, null, PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "openedAt")))
                .map(AccountResponse::from)
                .toList();
        return new CustomerDetailResponse(CustomerResponse.from(customer), accounts);
    }

    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        try {
            Customer customer = new Customer();
            apply(customer, request);
            customer.setCustomerNumber(sequenceService.nextCustomerNumber());
            customer.setCreatedBy(actor());
            customerRepository.save(customer);
            auditService.success("CUSTOMER_CREATED", "CUSTOMER", customer.getId().toString(), "Created customer " + customer.getCustomerNumber() + ".");
            return CustomerResponse.from(customer);
        } catch (ApiException ex) {
            auditService.failure("CUSTOMER_CREATE_FAILED", "CUSTOMER", null, ex.getMessage());
            throw ex;
        }
    }

    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        try {
            Customer customer = load(id);
            apply(customer, request);
            auditService.success("CUSTOMER_UPDATED", "CUSTOMER", customer.getId().toString(), "Updated customer " + customer.getCustomerNumber() + ".");
            return CustomerResponse.from(customer);
        } catch (ApiException ex) {
            auditService.failure("CUSTOMER_UPDATE_FAILED", "CUSTOMER", id.toString(), ex.getMessage());
            throw ex;
        }
    }

    @PreAuthorize("hasAuthority('CUSTOMER_DEACTIVATE')")
    @Transactional
    public CustomerResponse deactivate(UUID id) {
        Customer customer = load(id);
        if (customer.getStatus() == Enums.CustomerStatus.INACTIVE) {
            return CustomerResponse.from(customer);
        }
        long openAccounts = accountRepository.countByCustomerIdAndStatusNot(id, Enums.AccountStatus.CLOSED);
        if (openAccounts > 0) {
            auditService.failure("CUSTOMER_DEACTIVATE_FAILED", "CUSTOMER", id.toString(), "The customer still has open accounts.");
            throw ApiException.unprocessable("CUSTOMER_HAS_OPEN_ACCOUNTS", "Close every account before deactivating this customer.");
        }
        customer.setStatus(Enums.CustomerStatus.INACTIVE);
        auditService.success("CUSTOMER_DEACTIVATED", "CUSTOMER", id.toString(), "Deactivated customer " + customer.getCustomerNumber() + ".");
        return CustomerResponse.from(customer);
    }

    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    @Transactional
    public CustomerResponse reactivate(UUID id) {
        Customer customer = load(id);
        customer.setStatus(Enums.CustomerStatus.ACTIVE);
        auditService.success("CUSTOMER_REACTIVATED", "CUSTOMER", id.toString(), "Reactivated customer " + customer.getCustomerNumber() + ".");
        return CustomerResponse.from(customer);
    }

    private void apply(Customer customer, CustomerRequest request) {
        String email = request.email().trim().toLowerCase();
        boolean emailTaken = customer.getId() == null
                ? customerRepository.existsByEmailIgnoreCase(email)
                : customerRepository.existsByEmailIgnoreCaseAndIdNot(email, customer.getId());
        if (emailTaken) {
            throw ApiException.conflict("DUPLICATE_EMAIL", "A customer with this email already exists.");
        }
        LocalDate birthday = request.dateOfBirth();
        if (birthday.plusYears(18).isAfter(LocalDate.now(clock))) {
            throw ApiException.unprocessable("CUSTOMER_UNDERAGE", "The customer must be at least 18 years old.");
        }
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(email);
        customer.setPhone(Support.blankToNull(request.phone()));
        customer.setDateOfBirth(birthday);
        customer.setAddressLine(request.addressLine().trim());
        customer.setCity(request.city().trim());
        customer.setCountry(request.country().trim());
    }

    private Customer load(UUID id) {
        return customerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Customer was not found."));
    }

    private StaffUser actor() {
        return staffUserRepository.getReferenceById(CurrentActor.require().getId());
    }
}
