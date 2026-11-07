package com.smartdistribution.receivable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 核銷明細：一筆收款分配給一筆應收多少錢
 * 收款作廢時保留不刪；算已收金額時只加總 CONFIRMED 收款的核銷
 */
@Getter
@Entity
@Table(
        name = "payment_allocation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_allocation_receivable",
                columnNames = {"payment_id", "receivable_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receivable_id", nullable = false)
    private Receivable receivable;

    @Column(name = "allocated_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal allocatedAmount;

    public PaymentAllocation(Payment payment, Receivable receivable, BigDecimal allocatedAmount) {
        this.payment = payment;
        this.receivable = receivable;
        this.allocatedAmount = allocatedAmount;
    }
}
