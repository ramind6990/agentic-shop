package com.rmrx.agenticshop.ai.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, ConversationSummary.Key> {

    default Optional<ConversationSummary> find(Long ticketId, SummaryAudience audience) {
        return findById(new ConversationSummary.Key(ticketId, audience));
    }
}
