package lat.nexofood.api.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.security.custom.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration-time}")
    private Long expirationTime;

    @Value("${app.jwt.refresh-secret}")
    private String refreshSecretKey;

    @Value("${app.jwt.refresh-expiration}")
    private Long refreshExpirationTime;

    @Value("${app.jwt.issuer:nexofood-api}")
    private String issuer = "nexofood-api";

    @Value("${app.jwt.audience:nexofood-client}")
    private String audience = "nexofood-client";

    private SecretKey getSigningKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        int tokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        var builder = Jwts.builder()
                .subject(user.getEmail())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("type", TOKEN_TYPE_ACCESS)
                .claim("role", user.getSystemRole().name())
                .claim("tokenVersion", tokenVersion);
        if (user.getId() != null) {
            builder.claim("userId", user.getId().toString());
        }
        return builder
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey(secretKey))
                .compact();
    }

    public String generateRefreshToken(User user) {
        int tokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("type", TOKEN_TYPE_REFRESH)
                .claim("tokenVersion", tokenVersion);
        if (user.getId() != null) {
            builder.claim("userId", user.getId().toString());
        }
        return builder
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationTime))
                .signWith(getSigningKey(refreshSecretKey))
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject, secretKey);
    }

    public String extractUsernameFromRefreshToken(String token) {
        return extractClaim(token, Claims::getSubject, refreshSecretKey);
    }

    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", String.class), secretKey);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class), secretKey);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class), secretKey);
    }

    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> claims.get("tokenVersion", Integer.class), secretKey);
    }

    public String extractIssuer(String token) {
        return extractClaim(token, Claims::getIssuer, secretKey);
    }

    public Set<String> extractAudience(String token) {
        return extractClaim(token, Claims::getAudience, secretKey);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final Claims claims = extractAllClaims(token, secretKey);
            final String username = claims.getSubject();
            if (username == null || !username.equals(userDetails.getUsername())) {
                return false;
            }
            if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
                return false;
            }
            if (!TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class))) {
                return false;
            }
            if (issuer != null && !issuer.equals(claims.getIssuer())) {
                return false;
            }
            if (audience != null && (claims.getAudience() == null || !claims.getAudience().contains(audience))) {
                return false;
            }
            if (userDetails instanceof CustomUserDetails customUserDetails) {
                Integer claimVersion = claims.get("tokenVersion", Integer.class);
                int tokenVer = claimVersion != null ? claimVersion : 0;
                if (tokenVer != customUserDetails.getTokenVersion()) {
                    return false;
                }
            }
            return true;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isTokenValid(String token, User user) {
        return isTokenValid(token, new CustomUserDetails(user));
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        try {
            final Claims claims = extractAllClaims(token, refreshSecretKey);
            final String username = claims.getSubject();
            if (username == null || !username.equals(userDetails.getUsername())) {
                return false;
            }
            if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
                return false;
            }
            if (!TOKEN_TYPE_REFRESH.equals(claims.get("type", String.class))) {
                return false;
            }
            if (issuer != null && !issuer.equals(claims.getIssuer())) {
                return false;
            }
            if (audience != null && (claims.getAudience() == null || !claims.getAudience().contains(audience))) {
                return false;
            }
            if (userDetails instanceof CustomUserDetails customUserDetails) {
                Integer claimVersion = claims.get("tokenVersion", Integer.class);
                int tokenVer = claimVersion != null ? claimVersion : 0;
                if (tokenVer != customUserDetails.getTokenVersion()) {
                    return false;
                }
            }
            return true;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver, String secret) {
        final Claims claims = extractAllClaims(token, secret);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token, String secret) {
        return Jwts.parser()
                .verifyWith(getSigningKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public long getRefreshExpirationTime() {
        return refreshExpirationTime;
    }
}
