package com.rmrx.agenticshop.ai2.web;

import com.rmrx.agenticshop.ai2.events.ws.WsConnectTokenStore;
import com.rmrx.agenticshop.app.security.AccessScope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues a one-time connect token for the ai2 WebSocket ({@code /api/ai2/ws?connectToken=…}), for the signed-in
 * caller. The socket opened with it closes when the caller's JWT expires.
 */
@RestController("ai2WsConnectTokenController")
@RequestMapping("/api/ai2/ws")
@PreAuthorize("hasAuthority('AI_CHAT')")
public class WsConnectTokenController {

    private final WsConnectTokenStore connectTokens;
    private final AccessScope scope;

    public WsConnectTokenController(WsConnectTokenStore connectTokens, AccessScope scope) {
        this.connectTokens = connectTokens;
        this.scope = scope;
    }

    @PostMapping("/connect-tokens")
    public WsConnectTokenStore.Issued issue(@AuthenticationPrincipal Jwt jwt) {
        return connectTokens.issue(scope.current(), jwt.getExpiresAt());
    }
}
