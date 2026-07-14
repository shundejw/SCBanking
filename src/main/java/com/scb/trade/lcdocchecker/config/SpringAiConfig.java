package com.scb.trade.lcdocchecker.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link ChatClient} used by the Spring AI extraction pipeline. The OpenAI
 * starter auto-configures a {@link ChatClient.Builder} from the {@code spring.ai.openai.*}
 * properties (DeepSeek endpoint); we only need to call {@code build()}.
 */
@Configuration
public class SpringAiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
