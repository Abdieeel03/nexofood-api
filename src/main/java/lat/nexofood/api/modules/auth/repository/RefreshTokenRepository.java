package lat.nexofood.api.modules.auth.repository;

import jakarta.transaction.Transactional;
import lat.nexofood.api.modules.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.email = :email")
    void revokeAllUserTokens(@Param("email") String email);

    @Modifying
    @Transactional
    void deleteByExpiryDateBefore(LocalDateTime expiryDate);
}
