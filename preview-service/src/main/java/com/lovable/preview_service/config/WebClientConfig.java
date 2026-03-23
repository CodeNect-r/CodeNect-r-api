package com.lovable.preview_service.config;

import com.lovable.preview_service.security.HmacSignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final HmacSignatureService hmac;

    @Value("${internal.service.name}")
    private String serviceName;

    @Bean
    public WebClient webClient() {
        System.out.println("webclient is applied");
        return WebClient.builder()
                .filter((request, next) -> {

                    String timestamp = String.valueOf(Instant.now().getEpochSecond());

                    String signature =
                            hmac.generate(serviceName, request.url().getPath(), timestamp);

                    ClientRequest newRequest = ClientRequest.from(request)
                            .header("X-SERVICE-NAME", serviceName)
                            .header("X-TIMESTAMP", timestamp)
                            .header("X-SIGNATURE", signature)
                            .build();

                    return next.exchange(newRequest);
                })
                .build();
    }
}