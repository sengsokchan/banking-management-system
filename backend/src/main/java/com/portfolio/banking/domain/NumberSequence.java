package com.portfolio.banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "number_sequences")
public class NumberSequence {

    @Id
    @Column(name = "sequence_name", length = 50, nullable = false)
    private String sequenceName;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    public String getSequenceName() {
        return sequenceName;
    }

    public long getNextValue() {
        return nextValue;
    }

    public void setNextValue(long nextValue) {
        this.nextValue = nextValue;
    }
}
