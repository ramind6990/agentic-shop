package com.rmrx.agenticshop.ai.events;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration("aiEventsConfig")
@EnableConfigurationProperties(AiEventProperties.class)
class EventsConfig {
}
