package com.rmrx.agenticshop.ai2.tools.returns;

import com.rmrx.agenticshop.ai2.contract.domain.RefundStatus;
import com.rmrx.agenticshop.ai2.contract.domain.ReturnReason;
import com.rmrx.agenticshop.ai2.contract.domain.ReturnStatus;
import com.rmrx.agenticshop.ai2.contract.tool.response.returns.RefundItem;
import com.rmrx.agenticshop.ai2.contract.tool.response.returns.ReturnItem;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.ReturnDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Return and refund DTOs → ai2 contract, and the contract's filter values → domain values. */
@Mapper
public interface ReturnToolMapper {

    @Mapping(target = "orderNumber", source = "order.orderNumber")
    @Mapping(target = "itemId", source = "orderItemId")
    @Mapping(target = "product", source = "product.name")
    ReturnItem toItem(ReturnDto r);

    @Mapping(target = "orderNumber", source = "order.orderNumber")
    @Mapping(target = "returnId", source = "returnRequestId")
    RefundItem toItem(RefundDto r);

    List<com.rmrx.agenticshop.app.returns.entity.ReturnStatus> toReturnStatuses(List<ReturnStatus> statuses);

    List<com.rmrx.agenticshop.app.returns.entity.RefundStatus> toRefundStatuses(List<RefundStatus> statuses);

    com.rmrx.agenticshop.app.returns.entity.ReturnReason toDomain(ReturnReason reason);
}
