package com.lovable.project_service.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Service
@RequiredArgsConstructor
public class JwtService {

//    @Value("${jwt.secret}")
//    private String secret;
private String secret = "mysupersecurejwtsecretkeythatismorethan32byteslong";
    private Key getSigningKey() {

        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                this.secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
    public Claims extractAllClaims(String token) {
        try {
            // Clean the token string
            String cleanToken = token.trim();

            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(cleanToken)
                    .getBody();
        } catch (io.jsonwebtoken.security.SignatureException e) {
            System.out.println("!!! SIGNATURE MISMATCH !!!");
            System.out.println("Check 1: Is the secret EXACTLY the same string? Yes.");
            System.out.println("Check 2: Are both using UTF-8? Yes.");
            throw e;
        }
    }
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }@PostConstruct
    public void debug() {
        System.out.println("CRITICAL DEBUG: Secret is [" + secret + "]");
        System.out.println("CRITICAL DEBUG: Length is " + secret.length());
    }
}