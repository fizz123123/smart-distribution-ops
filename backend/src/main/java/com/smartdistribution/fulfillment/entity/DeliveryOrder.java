package com.smartdistribution.fulfillment.entity;

import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
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

import java.time.Instant;
import java.time.LocalDate;

/**
 * DO 表頭：一張 DO 只屬於一張 SO，只存 SO 的 ID；交貨地址與聯絡人從 SO 讀，不另存
 * 業務決定要出什麼、什麼時候出；倉管回報實際出了多少，所以 DO 建立後不提供修改
 */
@Getter
@Entity
@Table(name = "delivery_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryOrder extends TimestampedEntity {

    public static final String WITHDRAW_REASON = "業務撤回出貨通知";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "do_number", unique = true, length = 20)
    private String doNumber;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryOrderStatus status;

    //業務依客戶需求填寫
    @Column(name = "planned_shipment_date", nullable = false)
    private LocalDate plannedShipmentDate;

    //倉管交給司機、過帳時填寫；應收的到期日也用這個日期算
    @Column(name = "actual_shipment_date")
    private LocalDate actualShipmentDate;

    @Column(length = 500)
    private String remark;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    //完成揀貨時寫入
    @Column(name = "ready_by")
    private Long readyBy;

    @Column(name = "ready_at")
    private Instant readyAt;

    //出貨過帳時寫入
    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    //撤回或作廢時寫入
    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public DeliveryOrder(Long salesOrderId, LocalDate plannedShipmentDate, String remark, Long createdBy) {
        this.salesOrderId = salesOrderId;
        this.plannedShipmentDate = plannedShipmentDate;
        this.remark = normalizeOptional(remark);
        this.createdBy = createdBy;
        this.status = DeliveryOrderStatus.DRAFT;
    }

    public void assignNumber() {
        this.doNumber = "DO-%06d".formatted(id);
    }

    public boolean isCreatedBy(Long userId) {
        return createdBy.equals(userId);
    }

    public boolean isReady() {
        return status == DeliveryOrderStatus.READY;
    }

    //倉管完成揀貨，實際量已經寫到明細上；之後依實際量產生出貨單
    public void markReady(Long actorId, Instant at) {
        ensureStatus(DeliveryOrderStatus.DRAFT);

        this.readyBy = actorId;
        this.readyAt = at;
        this.status = DeliveryOrderStatus.READY;
    }

    //交給司機並過帳；同一張 DO 只能過帳一次
    public void post(LocalDate actualShipmentDate, Long actorId, Instant at) {
        ensureStatus(DeliveryOrderStatus.READY);

        this.actualShipmentDate = actualShipmentDate;
        this.postedBy = actorId;
        this.postedAt = at;
        this.status = DeliveryOrderStatus.POSTED;
    }

    //業務撤回自己的出貨通知：只有倉管還沒揀貨的草稿可以撤回
    public void withdraw(Long actorId, Instant at) {
        ensureStatus(DeliveryOrderStatus.DRAFT);

        markVoided(WITHDRAW_REASON, actorId, at);
    }

    //倉管作廢：草稿與已完成揀貨的都可以；已過帳的不能作廢
    public void voidOrder(String reason, Long actorId, Instant at) {
        if (status != DeliveryOrderStatus.DRAFT && status != DeliveryOrderStatus.READY) {
            throw new BusinessException(ErrorCode.DO_STATUS_CONFLICT);
        }

        markVoided(reason.strip(), actorId, at);
    }

    private void ensureStatus(DeliveryOrderStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.DO_STATUS_CONFLICT);
        }
    }

    private void markVoided(String reason, Long actorId, Instant at) {
        this.voidReason = reason;
        this.voidedBy = actorId;
        this.voidedAt = at;
        this.status = DeliveryOrderStatus.VOIDED;
    }

    //空白或只有空格的備註視為沒填，有內容就去掉前後空白
    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
