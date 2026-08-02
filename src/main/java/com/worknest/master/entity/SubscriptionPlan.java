package com.worknest.master.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Locale;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "monthly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyPrice = BigDecimal.ZERO;

    @Column(name = "yearly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal yearlyPrice = BigDecimal.ZERO;

    @Column(name = "billing_period", nullable = false, length = 30)
    private String billingPeriod = "MONTHLY";

    @Column(name = "badge", length = 60)
    private String badge;

    @Column(name = "recommended", nullable = false)
    private boolean recommended;

    @Column(name = "color", length = 30)
    private String color = "#2563eb";

    @Column(name = "icon", length = 60)
    private String icon = "package";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @PrePersist
    @PreUpdate
    void normalize() {
        name = name == null ? null : name.trim();
        code = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
        description = description == null || description.isBlank() ? null : description.trim();
        monthlyPrice = monthlyPrice == null ? BigDecimal.ZERO : monthlyPrice;
        yearlyPrice = yearlyPrice == null ? BigDecimal.ZERO : yearlyPrice;
        billingPeriod = billingPeriod == null || billingPeriod.isBlank()
                ? "MONTHLY"
                : billingPeriod.trim().toUpperCase(Locale.ROOT);
        badge = badge == null || badge.isBlank() ? null : badge.trim();
        color = color == null || color.isBlank() ? "#2563eb" : color.trim();
        icon = icon == null || icon.isBlank() ? "package" : icon.trim();
    }
}
