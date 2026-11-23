package com.rmrx.agenticshop.support.service;

import com.rmrx.agenticshop.common.dto.RefMapper;
import com.rmrx.agenticshop.returns.service.ReturnMapper;
import com.rmrx.agenticshop.support.dto.TicketDtos.MessageDto;
import com.rmrx.agenticshop.support.dto.TicketDtos.TicketDetail;
import com.rmrx.agenticshop.support.dto.TicketDtos.TicketSummary;
import com.rmrx.agenticshop.support.entity.Ticket;
import com.rmrx.agenticshop.support.entity.TicketMessage;
import org.mapstruct.Mapper;

@Mapper(uses = {RefMapper.class, ReturnMapper.class})
public interface TicketMapper {

    TicketSummary toSummary(Ticket ticket);

    TicketDetail toDetail(Ticket ticket, long messageCount);

    MessageDto toMessage(TicketMessage message);
}
