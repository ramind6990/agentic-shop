package com.rmrx.agenticshop.ai2.events;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration("ai2EventsConfig")
@EnableConfigurationProperties(Ai2EventProperties.class)
class EventsConfig {
}
