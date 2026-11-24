package com.rmrx.agenticshop.ai.action.repository;

import com.rmrx.agenticshop.ai.action.entity.ActionTargetType;
import com.rmrx.agenticshop.ai.action.entity.AiAction;
import com.rmrx.agenticshop.ai.contract.response.ActionState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiActionRepository extends JpaRepository<AiAction, Long> {

    Optional<AiAction> findByTokenHash(String tokenHash);

    List<AiAction> findByTargetTypeAndTargetIdAndState(ActionTargetType targetType, Long targetId, ActionState state);

    default List<AiAction> findOpenByTarget(ActionTargetType targetType, Long targetId) {
        return findByTargetTypeAndTargetIdAndState(targetType, targetId, ActionState.OPEN);
    }

    List<AiAction> findByTicketIdAndState(Long ticketId, ActionState state);
}
