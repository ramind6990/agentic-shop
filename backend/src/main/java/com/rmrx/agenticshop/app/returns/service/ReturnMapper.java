package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.common.dto.Ref;
import com.rmrx.agenticshop.app.common.dto.RefMapper;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.OrderRef;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundApprovalDto;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.app.returns.entity.RefundApproval;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.ReturnDto;
import com.rmrx.agenticshop.app.returns.entity.Refund;
import com.rmrx.agenticshop.app.returns.entity.ReturnRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = RefMapper.class)
public interface ReturnMapper {

    @Mapping(target = "orderItemId", source = "orderItem.id")
    @Mapping(target = "product", expression = "java(productOf(r))")
    ReturnDto toReturn(ReturnRequest r);

    @Mapping(target = "returnRequestId", source = "returnRequest.id")
    RefundDto toRefund(Refund refund);

    @Mapping(target = "currency", source = "order.currency")
    @Mapping(target = "returnRequestId", source = "returnRequest.id")
    @Mapping(target = "refundId", source = "refund.id")
    @Mapping(target = "ticketNumber", source = "ticket.ticketNumber")
    RefundApprovalDto toApproval(RefundApproval approval);

    default OrderRef order(CustomerOrder o) {
        return o == null ? null : new OrderRef(o.getId(), o.getOrderNumber());
    }

    /** Uses the order-line snapshot title, which is what the customer bought. */
    default Ref productOf(ReturnRequest r) {
        var item = r.getOrderItem();
        return new Ref(item.getProduct().getId(), item.getProductTitle());
    }
}
