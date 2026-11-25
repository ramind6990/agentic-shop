package com.rmrx.agenticshop.app.support.service;

import com.rmrx.agenticshop.app.common.dto.RefMapper;
import com.rmrx.agenticshop.app.returns.service.ReturnMapper;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.MessageDto;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketDetail;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketSummary;
import com.rmrx.agenticshop.app.support.entity.Ticket;
import com.rmrx.agenticshop.app.support.entity.TicketMessage;
import org.mapstruct.Mapper;

@Mapper(uses = {RefMapper.class, ReturnMapper.class})
public interface TicketMapper {

    TicketSummary toSummary(Ticket ticket);

    TicketDetail toDetail(Ticket ticket, long messageCount);

    MessageDto toMessage(TicketMessage message);
}
