package com.lovable.ai_service.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

// 2. Jackson (JSON) Imports
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
@Configuration
public class AiConfig {


//    @Bean
//    @Primary
//    public OpenAiChatModel openAiChatModel(
//            OpenAiApi openAiApi,
//            @Value("${spring.ai.openai.chat.options.model}") String modelName) {
//
//        return OpenAiChatModel.builder()
//                .openAiApi(openAiApi)
//                .defaultOptions(OpenAiChatOptions.builder()
//                        .model(modelName)
//                        .build())
//                .build();
//    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}