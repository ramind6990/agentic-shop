package com.rmrx.agenticshop.ai.tools.support;

import com.rmrx.agenticshop.ai.contract.domain.SupportTier;
import com.rmrx.agenticshop.ai.contract.domain.TicketCategory;
import com.rmrx.agenticshop.ai.contract.domain.TicketPriority;
import com.rmrx.agenticshop.ai.contract.domain.TicketStatus;
import com.rmrx.agenticshop.ai.contract.tool.response.support.TicketDetailResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.support.TicketItem;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.MessageDto;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketDetail;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Ticket DTOs → ai support contract, and the contract's filter values → domain values. */
@Mapper
public interface SupportToolMapper {

    @Mapping(target = "orderNumber", source = "order.orderNumber")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customer", source = "customer.name")
    @Mapping(target = "assignee", source = "assignee.name")
    TicketItem toItem(TicketSummary ticket);

    @Mapping(target = "orderNumber", source = "order.orderNumber")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customer", source = "customer.name")
    @Mapping(target = "assignee", source = "assignee.name")
    TicketItem toItem(TicketDetail ticket);

    @Mapping(target = "author", source = "authorType")
    @Mapping(target = "authorName", source = "author.name")
    TicketDetailResponse.Message toMessage(MessageDto message);

    List<com.rmrx.agenticshop.app.support.entity.TicketStatus> toStatuses(List<TicketStatus> statuses);

    com.rmrx.agenticshop.app.support.entity.TicketCategory toDomain(TicketCategory category);

    com.rmrx.agenticshop.app.support.entity.TicketPriority toDomain(TicketPriority priority);

    com.rmrx.agenticshop.app.support.entity.SupportTier toDomain(SupportTier tier);
}
