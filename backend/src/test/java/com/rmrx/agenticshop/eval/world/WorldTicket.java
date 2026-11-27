package com.rmrx.agenticshop.eval.world;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rmrx.agenticshop.app.support.entity.SupportTier;
import com.rmrx.agenticshop.app.support.entity.TicketCategory;
import com.rmrx.agenticshop.app.support.entity.TicketPriority;
import com.rmrx.agenticshop.app.support.entity.TicketStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A support ticket: seeded by the scenario, or opened by a hand-off during the run. */
public class WorldTicket {

    public Long id;
    public String number;
    /** Username of the customer; default: the actor. */
    public String owner;
    public String subject;
    public TicketCategory category = TicketCategory.ORDER;
    public TicketStatus status = TicketStatus.OPEN;
    public TicketPriority priority = TicketPriority.NORMAL;
    public SupportTier tier = SupportTier.T1;
    /** The order number it is about, if any. */
    public String order;
    public String created = "-1d";
    public List<WorldMessage> messages = new ArrayList<>();

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long ownerId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Instant createdAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Instant updatedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long conversationId;
}
