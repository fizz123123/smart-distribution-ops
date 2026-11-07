package com.smartdistribution.receivable.entity;

import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 應收表頭：一張已過帳的 DO 對應一筆應收
 * 金額、付款條件、到期日在建立時算好，之後不再修改；金額有問題要回頭查 SO 或 DO
 * DO 編號、SO 編號與實際出貨日是建立當下的快照，清單不必再查出貨與銷售模組
 */
@Getter
@Entity
@Table(name = "receivable")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receivable extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receivable_number", unique = true, length = 20)
    private String receivableNumber;

    @Column(name = "delivery_order_id", nullable = false, unique = true)
    private Long deliveryOrderId;

    @Column(name = "do_number", nullable = false, length = 20)
    private String doNumber;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "so_number", nullable = false, length = 20)
    private String soNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceivableStatus status;

    //SO 送審時的付款條件快照
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_term", nullable = false, length = 10)
    private PaymentTerm paymentTerm;

    @Column(name = "actual_shipment_date", nullable = false)
    private LocalDate actualShipmentDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "untaxed_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal untaxedAmount;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal totalAmount;

    @Version
    @Column(nullable = false)
    private Long version;

    public Receivable(
            Long deliveryOrderId,
            String doNumber,
            Long salesOrderId,
            String soNumber,
            Long customerId,
            PaymentTerm paymentTerm,
            LocalDate actualShipmentDate,
            BigDecimal untaxedAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount
    ) {
        this.deliveryOrderId = deliveryOrderId;
        this.doNumber = doNumber;
        this.salesOrderId = salesOrderId;
        this.soNumber = soNumber;
        this.customerId = customerId;
        this.paymentTerm = paymentTerm;
        this.actualShipmentDate = actualShipmentDate;
        this.dueDate = paymentTerm.dueDate(actualShipmentDate);
        this.untaxedAmount = untaxedAmount;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.status = ReceivableStatus.PENDING_CONFIRMATION;
    }

    public void assignNumber() {
        this.receivableNumber = "AR-%06d".formatted(id);
    }
}
