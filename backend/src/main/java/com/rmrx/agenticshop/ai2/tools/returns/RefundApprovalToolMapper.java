package com.rmrx.agenticshop.ai2.tools.returns;

import com.rmrx.agenticshop.ai2.contract.domain.RefundApprovalQueue;
import com.rmrx.agenticshop.ai2.contract.domain.RefundApprovalStatus;
import com.rmrx.agenticshop.ai2.contract.tool.response.returns.RefundApprovalItem;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundApprovalDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Refund approval DTOs → ai2 contract, and the contract's filter values → domain values. */
@Mapper
public interface RefundApprovalToolMapper {

    @Mapping(target = "orderNumber", source = "order.orderNumber")
    @Mapping(target = "requestedBy", source = "requestedBy.name")
    @Mapping(target = "decidedBy", source = "decidedBy.name")
    @Mapping(target = "returnId", source = "returnRequestId")
    RefundApprovalItem toItem(RefundApprovalDto approval);

    com.rmrx.agenticshop.app.returns.entity.RefundApprovalStatus toDomain(RefundApprovalStatus status);

    com.rmrx.agenticshop.app.returns.service.RefundApprovalQueue toDomain(RefundApprovalQueue queue);
}
