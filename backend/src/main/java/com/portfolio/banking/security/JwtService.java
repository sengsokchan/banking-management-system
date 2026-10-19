package com.portfolio.banking.security;

import com.portfolio.banking.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String createAccessToken(StaffPrincipal principal) {
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(properties.getJwt().getAccessTokenMinutes() * 60);
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
    }

    public long accessTokenSeconds() {
        return properties.getJwt().getAccessTokenMinutes() * 60;
    }

    public ParsedAccessToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clockSkewSeconds(30)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String uid = claims.get("uid", String.class);
            return new ParsedAccessToken(UUID.fromString(uid), claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            throw ex;
        }
    }

    public record ParsedAccessToken(UUID userId, String username) {
    }
}
