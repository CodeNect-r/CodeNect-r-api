package com.lovable.project_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private final HmacSignatureService hmac;

    private static final long ALLOWED_CLOCK_SKEW = 30;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String service = request.getHeader("X-SERVICE-NAME");
        String timestamp = request.getHeader("X-TIMESTAMP");
        String signature = request.getHeader("X-SIGNATURE");

        if (service == null && timestamp == null && signature == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {

            long ts = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();

            if (Math.abs(now - ts) > ALLOWED_CLOCK_SKEW) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            String path = request.getRequestURI();

            boolean valid =
                    hmac.verify(service, path, timestamp, signature);

            if (!valid) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            service,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))
                    );

            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception e) {

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}