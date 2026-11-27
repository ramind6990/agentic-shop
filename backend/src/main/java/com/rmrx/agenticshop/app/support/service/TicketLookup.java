package com.rmrx.agenticshop.app.support.service;

import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.support.entity.Ticket;
import com.rmrx.agenticshop.app.support.repository.TicketRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Loads one ticket by number, for callers allowed to see it; anything else is not found. */
@Component
public class TicketLookup {

    private final TicketRepository tickets;
    private final AccessScope scope;

    public TicketLookup(TicketRepository tickets, AccessScope scope) {
        this.tickets = tickets;
        this.scope = scope;
    }

    public Ticket require(String ticketNumber) {
        String key = ticketNumber == null ? "" : ticketNumber.trim().toUpperCase(Locale.ROOT);
        Ticket t = tickets.findByTicketNumber(key).orElseThrow(() -> new NotFoundException("Ticket", ticketNumber));
        scope.requireOwned(t.getCustomer().getId(), t.getSeller() == null ? null : t.getSeller().getId(), "Ticket",
                ticketNumber);
        return t;
    }
}
