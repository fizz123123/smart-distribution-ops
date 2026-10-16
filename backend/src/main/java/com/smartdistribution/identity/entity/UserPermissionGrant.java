package com.smartdistribution.identity.entity;

import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
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

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "user_permission_grant",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_permission_grant_user_id_permission_id",
                columnNames = {"user_id", "permission_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPermissionGrant extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    public UserPermissionGrant(
            AppUser user,
            Permission permission,
            String reason,
            Instant validFrom,
            Instant validTo
    ) {
        this.user = user;
        this.permission = permission;
        this.reason = reason;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public boolean isEffectiveAt(Instant instant) {
        boolean isStarted = !validFrom.isAfter(instant);
        boolean isNotEnded = validTo == null || validTo.isAfter(instant);
        return isStarted && isNotEnded;
    }
}
