package com.lovable.ai_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class HmacSignatureService {

    @Value("${internal.shared.secret}")
    private String sharedSecret;

    public String generate(String serviceName, String path, String timestamp) {

        try {

            String data = serviceName + path + timestamp;

            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec key =
                    new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

            mac.init(key);

            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(raw);

        } catch (Exception e) {
            throw new RuntimeException("HMAC generation failed", e);
        }
    }

    public boolean verify(String serviceName, String path, String timestamp, String signature) {

        String expected = generate(serviceName, path, timestamp);

        return expected.equals(signature);
    }
}