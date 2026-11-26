package com.hrflow.security;

import com.hrflow.config.AppProperties;
import com.hrflow.domain.Employee;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies HS256 JSON Web Tokens.
 *
 * <p>The token's subject is the employee code; custom claims carry the numeric
 * id and role so the auth filter can rebuild the principal without a DB lookup
 * (stateless authentication).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMinutes;
    private final String issuer;

    public JwtService(AppProperties props) {
        // hmacShaKeyFor requires >= 32 bytes for HS256; enforced by config default.
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = props.jwt().expiryMinutes();
        this.issuer = props.jwt().issuer();
    }

    /** Create a signed token for a successfully authenticated employee. */
    public String issue(Employee employee) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(employee.getEmployeeCode())
                .claim("eid", employee.getId())
                .claim("role", employee.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiryMinutes * 60)))
                .signWith(key)
                .compact();
    }

    /** Verify the signature + expiry and return the claims, or throw if invalid. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
