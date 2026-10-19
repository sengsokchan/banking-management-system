package com.portfolio.banking.service;

import com.portfolio.banking.domain.NumberSequence;
import com.portfolio.banking.repo.NumberSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SequenceService {

    private final NumberSequenceRepository numberSequenceRepository;

    public SequenceService(NumberSequenceRepository numberSequenceRepository) {
        this.numberSequenceRepository = numberSequenceRepository;
    }

    @Transactional
    public String nextCustomerNumber() {
        return "C-" + next("customer");
    }

    @Transactional
    public String nextAccountNumber() {
        return Long.toString(next("account"));
    }

    @Transactional
    public String nextTransactionReference() {
        return "MT-" + next("transaction");
    }

    private long next(String name) {
        NumberSequence sequence = numberSequenceRepository.findForUpdate(name)
                .orElseThrow(() -> new IllegalStateException("Number sequence was not found: " + name));
        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        return value;
    }
}
