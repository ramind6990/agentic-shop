package com.rmrx.agenticshop.app.identity.service;

import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.identity.dto.UserDtos.AddressDto;
import com.rmrx.agenticshop.app.identity.entity.Role;
import com.rmrx.agenticshop.app.identity.repository.AddressRepository;
import com.rmrx.agenticshop.app.identity.repository.AppUserRepository;
import com.rmrx.agenticshop.app.security.AccessScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Saved addresses of a customer: customers see their own, staff see any customer's. */
@Service
@Transactional(readOnly = true)
public class AddressService {

    private final AddressRepository addresses;
    private final AppUserRepository users;
    private final UserMapper mapper;
    private final AccessScope scope;

    public AddressService(AddressRepository addresses, AppUserRepository users, UserMapper mapper,
                          AccessScope scope) {
        this.addresses = addresses;
        this.users = users;
        this.mapper = mapper;
        this.scope = scope;
    }

    /** Default address first. */
    public List<AddressDto> addresses(Long customerId) {
        Long owner = scope.customerFilter(customerId);
        if (owner == null) throw new BadRequestException("Which customer? A customerId is required.");
        users.findById(owner).filter(u -> u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new NotFoundException("Customer", owner));
        return addresses.findByUserIdOrderByDefaultAddressDescIdAsc(owner).stream().map(mapper::toAddress).toList();
    }
}
