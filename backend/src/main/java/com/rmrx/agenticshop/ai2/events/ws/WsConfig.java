package com.rmrx.agenticshop.ai2.events.ws;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * The ai2 chat socket at {@value #PATH}: allowed origins, the connect-token handshake, and the largest client frame.
 * The HTTP security chain lets the handshake through; the connect token authenticates it. (Idle sockets are closed by
 * {@link ClientSession}, from missing pongs: the container's idle timeout also counts the server's own pings.)
 */
@Configuration("ai2WsConfig")
@EnableWebSocket
@EnableConfigurationProperties(Ai2WsProperties.class)
class WsConfig implements WebSocketConfigurer {

    static final String PATH = "/api/ai2/ws";

    private final ChatSocketHandler handler;
    private final WsHandshakeInterceptor handshake;
    private final Ai2WsProperties props;

    WsConfig(ChatSocketHandler handler, WsHandshakeInterceptor handshake, Ai2WsProperties props) {
        this.handler = handler;
        this.handshake = handshake;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, PATH)
                .addInterceptors(handshake)
                .setAllowedOrigins(props.allowedOrigins().toArray(String[]::new));
    }

    @Bean
    ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(props.maxMessageBytes());
        container.setMaxBinaryMessageBufferSize(1024);
        return container;
    }
}
