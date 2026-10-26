package com.quickpay;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class JwtService {
    private final SecretKey key;

    public JwtService(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(long customerId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(Long.toString(customerId))
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(60 * 60 * 12)))
            .signWith(key)
            .compact();
    }

    public long verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }
}
