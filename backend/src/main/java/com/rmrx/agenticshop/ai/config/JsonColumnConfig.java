package com.rmrx.agenticshop.ai.config;

import org.hibernate.cfg.MappingSettings;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes Hibernate read and write JSON columns with Jackson 3.
 *
 * <p>Both Jackson 2 (pulled in by LangChain4j) and Jackson 3 (Spring Boot 4) are on the classpath, and Hibernate's
 * auto-detection prefers Jackson 2. Its mapper cannot turn jsonb into a Jackson 3 {@code tools.jackson.databind.JsonNode},
 * which the jsonb fields ({@code AiAction.params}, {@code TicketMessage.payload}) use. Verified by
 * {@code POST /api/ai/dev/json-roundtrip}.
 */
@Configuration(proxyBeanMethods = false)
public class JsonColumnConfig {

    @Bean
    HibernatePropertiesCustomizer jackson3JsonFormatMapper() {
        return props -> props.put(MappingSettings.JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper());
    }
}
