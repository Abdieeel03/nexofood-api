package lat.nexofood.api.modules.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lat.nexofood.api.common.model.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class User extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @ToString.Exclude
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 50)
    @Builder.Default
    private UserSystemRole systemRole = UserSystemRole.USER;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Integer tokenVersion = 0;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    /** Soft delete: fecha en que el usuario fue dado de baja lógicamente. */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public boolean isAccountLocked() {
        return lockedUntil != null && lockedUntil.isAfter(OffsetDateTime.now());
    }

    public boolean recordFailedLogin(int maxAttempts, long lockMinutes) {
        int attempts = (this.failedLoginAttempts == null ? 0 : this.failedLoginAttempts) + 1;
        this.failedLoginAttempts = attempts;
        if (attempts >= maxAttempts) {
            this.lockedUntil = OffsetDateTime.now().plusMinutes(lockMinutes);
            return true;
        }
        return false;
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void incrementTokenVersion() {
        this.tokenVersion = (this.tokenVersion == null ? 0 : this.tokenVersion) + 1;
    }
}
