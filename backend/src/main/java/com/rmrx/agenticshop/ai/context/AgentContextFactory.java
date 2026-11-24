package com.rmrx.agenticshop.ai.context;

import com.rmrx.agenticshop.identity.entity.Role;
import com.rmrx.agenticshop.security.CurrentUser;
import com.rmrx.agenticshop.support.entity.SupportTier;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Builds the {@link AgentContext} of a turn from the signed-in user. Call it on the HTTP request thread. */
@Component
public class AgentContextFactory {

    public AgentContext from(CurrentUser user, Long ticketId) {
        return new AgentContext(user.id(), user.username(), user.role(), user.sellerId(), tierOf(user.role()),
                ticketId, UUID.randomUUID().toString());
    }

    private static SupportTier tierOf(Role role) {
        return switch (role) {
            case SUPPORT_T1 -> SupportTier.T1;
            case SUPPORT_T2 -> SupportTier.T2;
            default -> null;
        };
    }
}
