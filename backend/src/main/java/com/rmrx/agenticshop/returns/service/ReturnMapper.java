package com.rmrx.agenticshop.returns.service;

import com.rmrx.agenticshop.common.dto.Ref;
import com.rmrx.agenticshop.common.dto.RefMapper;
import com.rmrx.agenticshop.order.entity.CustomerOrder;
import com.rmrx.agenticshop.returns.dto.ReturnDtos.OrderRef;
import com.rmrx.agenticshop.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.returns.dto.ReturnDtos.ReturnDto;
import com.rmrx.agenticshop.returns.entity.Refund;
import com.rmrx.agenticshop.returns.entity.ReturnRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = RefMapper.class)
public interface ReturnMapper {

    @Mapping(target = "orderItemId", source = "orderItem.id")
    @Mapping(target = "product", expression = "java(productOf(r))")
    ReturnDto toReturn(ReturnRequest r);

    @Mapping(target = "returnRequestId", source = "returnRequest.id")
    RefundDto toRefund(Refund refund);

    default OrderRef order(CustomerOrder o) {
        return o == null ? null : new OrderRef(o.getId(), o.getOrderNumber());
    }

    /** Uses the order-line snapshot title, which is what the customer bought. */
    default Ref productOf(ReturnRequest r) {
        var item = r.getOrderItem();
        return new Ref(item.getProduct().getId(), item.getProductTitle());
    }
}
