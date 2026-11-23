package com.rmrx.agenticshop.support.repository;

import com.rmrx.agenticshop.support.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    @EntityGraph(attributePaths = "author")
    Page<TicketMessage> findByTicketId(Long ticketId, Pageable pageable);

    long countByTicketId(Long ticketId);
}
