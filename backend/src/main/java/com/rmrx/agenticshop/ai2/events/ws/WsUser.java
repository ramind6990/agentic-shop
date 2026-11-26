package com.rmrx.agenticshop.ai2.events.ws;

import com.rmrx.agenticshop.app.security.CurrentUser;

import java.time.Instant;

/**
 * Who a socket belongs to, set once at the handshake from a connect token.
 *
 * @param expiresAt when the JWT the connect token was issued for expires; the socket is closed then
 */
public record WsUser(CurrentUser user, Instant expiresAt) {

    /** Session attribute holding the socket's {@link WsUser}. */
    public static final String ATTRIBUTE = "ai2.wsUser";
}
