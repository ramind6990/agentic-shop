package com.rmrx.agenticshop.app.support.repository;

import com.rmrx.agenticshop.app.support.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    long countByCustomerId(Long customerId);
}
