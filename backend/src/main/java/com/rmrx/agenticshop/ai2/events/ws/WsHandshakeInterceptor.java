package com.rmrx.agenticshop.ai2.events.ws;

import com.rmrx.agenticshop.app.security.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * Authenticates and authorizes the handshake: {@code ?connectToken=} must be a valid, unused connect token
 * ({@link WsConnectTokenStore}), else 401; its user must hold {@code AI_CHAT}, else 403. On success the socket's
 * {@link WsUser} goes into the session attributes. (The allowed origins are checked before this.)
 */
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PARAM = "connectToken";

    private final WsConnectTokenStore connectTokens;

    public WsHandshakeInterceptor(WsConnectTokenStore connectTokens) {
        this.connectTokens = connectTokens;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst(PARAM);
        Optional<WsUser> user = connectTokens.consume(token);
        if (user.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (!user.get().user().has(Permission.AI_CHAT)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(WsUser.ATTRIBUTE, user.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
    }
}
