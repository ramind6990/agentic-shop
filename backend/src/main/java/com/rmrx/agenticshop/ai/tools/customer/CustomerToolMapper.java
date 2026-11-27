package com.rmrx.agenticshop.ai.tools.customer;

import com.rmrx.agenticshop.ai.contract.domain.Address;
import com.rmrx.agenticshop.ai.contract.tool.response.customer.CustomerDetailResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.customer.CustomerListResponse;
import com.rmrx.agenticshop.app.identity.dto.UserDtos.AddressDto;
import com.rmrx.agenticshop.app.identity.dto.UserDtos.CustomerDetail;
import com.rmrx.agenticshop.app.identity.dto.UserDtos.CustomerSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Customer DTOs → ai customer contract (no risk flags: support does not see them). */
@Mapper
public interface CustomerToolMapper {

    Address toAddress(AddressDto address);

    CustomerListResponse.Customer toCustomer(CustomerSummary customer);

    @Mapping(target = "orderCount", source = "stats.orderCount")
    @Mapping(target = "returnCount", source = "stats.returnCount")
    @Mapping(target = "ticketCount", source = "stats.ticketCount")
    CustomerDetailResponse toDetail(CustomerDetail customer);
}
