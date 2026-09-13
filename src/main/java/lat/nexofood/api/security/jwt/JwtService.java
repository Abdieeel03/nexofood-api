package lat.nexofood.api.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lat.nexofood.api.modules.auth.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration-time}")
    private Long expirationTime;

    @Value("${app.jwt.refresh-secret}")
    private String refreshSecretKey;

    @Value("${app.jwt.refresh-expiration}")
    private Long refreshExpirationTime;

    private SecretKey getSigningKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        var builder = Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getSystemRole().name());
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
        var builder = Jwts.builder()
                .subject(user.getEmail())
                .claim("type", "refresh");
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

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username != null && username.equals(userDetails.getUsername()) && !isTokenExpired(token, secretKey));
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsernameFromRefreshToken(token);
            return (username != null && username.equals(userDetails.getUsername()) && !isTokenExpired(token, refreshSecretKey));
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token, String secret) {
        return extractExpiration(token, secret).before(new Date());
    }

    private Date extractExpiration(String token, String secret) {
        return extractClaim(token, Claims::getExpiration, secret);
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
