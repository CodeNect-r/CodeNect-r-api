package com.lovable.chat_service.config;

import com.lovable.chat_service.security.StompPrincipal;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Component
public class UserHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {

        String email = (String) attributes.get(WebSocketAuthInterceptor.WS_USER_EMAIL);

        if (email == null || email.isBlank()) {
            email = "anonymous-" + UUID.randomUUID();
        }

        return new StompPrincipal(email);
    }
}