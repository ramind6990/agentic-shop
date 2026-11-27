package com.rmrx.agenticshop.app.support.repository;

import com.rmrx.agenticshop.app.support.entity.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    long countByCustomerId(Long customerId);

    @EntityGraph(attributePaths = {"customer", "order", "seller", "assignee"})
    Optional<Ticket> findByTicketNumber(String ticketNumber);

    /** The customer's latest ticket about an order that is not in one of the given statuses. */
    Optional<Ticket> findFirstByCustomerIdAndOrderIdAndStatusNotInOrderByIdDesc(Long customerId, Long orderId,
                                                                                 Collection<TicketStatus> statuses);

    /** The customer's latest ticket from a conversation that is not in one of the given statuses. */
    Optional<Ticket> findFirstByCustomerIdAndConversationIdAndStatusNotInOrderByIdDesc(
            Long customerId, Long conversationId, Collection<TicketStatus> statuses);
}
