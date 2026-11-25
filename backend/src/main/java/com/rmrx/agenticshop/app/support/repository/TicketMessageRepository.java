package com.rmrx.agenticshop.app.support.repository;

import com.rmrx.agenticshop.app.support.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    @EntityGraph(attributePaths = "author")
    Page<TicketMessage> findByTicketId(Long ticketId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<TicketMessage> findByTicketIdAndInternalFalse(Long ticketId, Pageable pageable);

    long countByTicketId(Long ticketId);

    long countByTicketIdAndInternalFalse(Long ticketId);

    // conversation memory: the timeline after a summary cursor (message id), all or customer-visible only

    @EntityGraph(attributePaths = "author")
    List<TicketMessage> findByTicketIdAndIdGreaterThan(Long ticketId, Long afterId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    List<TicketMessage> findByTicketIdAndInternalFalseAndIdGreaterThan(Long ticketId, Long afterId, Pageable pageable);

    long countByTicketIdAndIdGreaterThan(Long ticketId, Long afterId);

    long countByTicketIdAndInternalFalseAndIdGreaterThan(Long ticketId, Long afterId);
}
