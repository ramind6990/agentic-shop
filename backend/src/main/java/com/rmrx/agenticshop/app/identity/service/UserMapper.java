package com.rmrx.agenticshop.app.identity.service;

import com.rmrx.agenticshop.app.common.dto.RefMapper;
import com.rmrx.agenticshop.app.identity.dto.UserDtos.*;
import com.rmrx.agenticshop.app.identity.entity.Address;
import com.rmrx.agenticshop.app.identity.entity.AppUser;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(uses = RefMapper.class)
public interface UserMapper {

    CustomerSummary toCustomerSummary(AppUser user);

    CustomerDetail toCustomerDetail(AppUser user, List<AddressDto> addresses, CustomerStats stats);

    AddressDto toAddress(Address address);

    UserDto toUser(AppUser user);
}
