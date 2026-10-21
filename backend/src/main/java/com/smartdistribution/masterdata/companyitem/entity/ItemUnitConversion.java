package com.smartdistribution.masterdata.companyitem.entity;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Getter
@Entity
@Table(
        name = "item_unit_conversion",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_item_unit_conversion_item_unit",
                columnNames = {"company_item_id", "unit_code"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemUnitConversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_item_id", nullable = false)
    private CompanyItem companyItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_code", nullable = false, length = 10)
    private UnitOfMeasure unit;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal factor;

    public ItemUnitConversion(CompanyItem companyItem, UnitOfMeasure unit, BigDecimal factor) {
        this.companyItem = companyItem;
        this.unit = unit;
        this.factor = factor;
    }
}
