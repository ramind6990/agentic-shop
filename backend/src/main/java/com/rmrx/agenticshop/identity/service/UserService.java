package com.rmrx.agenticshop.identity.service;

import com.rmrx.agenticshop.catalog.dto.ProductDtos.CustomerReviewDto;
import com.rmrx.agenticshop.catalog.repository.ReviewRepository;
import com.rmrx.agenticshop.catalog.service.CatalogMapper;
import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import com.rmrx.agenticshop.common.repository.Specs;
import com.rmrx.agenticshop.identity.dto.UserDtos.*;
import com.rmrx.agenticshop.identity.entity.AppUser;
import com.rmrx.agenticshop.identity.entity.Role;
import com.rmrx.agenticshop.identity.entity.UserStatus;
import com.rmrx.agenticshop.identity.repository.AddressRepository;
import com.rmrx.agenticshop.identity.repository.AppUserRepository;
import com.rmrx.agenticshop.order.repository.CustomerOrderRepository;
import com.rmrx.agenticshop.returns.repository.ReturnRequestRepository;
import com.rmrx.agenticshop.risk.entity.FlagSubjectType;
import com.rmrx.agenticshop.risk.repository.AccountFlagRepository;
import com.rmrx.agenticshop.support.repository.TicketRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CustomerOrderRepository orderRepository;
    private final ReturnRequestRepository returnRepository;
    private final TicketRepository ticketRepository;
    private final AccountFlagRepository flagRepository;
    private final ReviewRepository reviewRepository;
    private final UserMapper mapper;
    private final CatalogMapper catalogMapper;

    public UserService(AppUserRepository userRepository, AddressRepository addressRepository,
                       CustomerOrderRepository orderRepository, ReturnRequestRepository returnRepository,
                       TicketRepository ticketRepository, AccountFlagRepository flagRepository,
                       ReviewRepository reviewRepository, UserMapper mapper, CatalogMapper catalogMapper) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.returnRepository = returnRepository;
        this.ticketRepository = ticketRepository;
        this.flagRepository = flagRepository;
        this.reviewRepository = reviewRepository;
        this.mapper = mapper;
        this.catalogMapper = catalogMapper;
    }

    public PageResponse<CustomerSummary> searchCustomers(String q, UserStatus status, Pageable pageable) {
        Specification<AppUser> spec = Specification.allOf(
                Specs.eq("role", Role.CUSTOMER),
                Specs.eq("status", status),
                Specs.containsIgnoreCase(q, "fullName", "email"));
        return PageResponse.of(userRepository.findAll(spec, pageable), mapper::toCustomerSummary);
    }

    public CustomerDetail getCustomer(Long id) {
        AppUser user = requireCustomer(id);
        List<AddressDto> addresses = addressRepository.findByUserIdOrderByDefaultAddressDescIdAsc(id).stream()
                .map(mapper::toAddress).toList();
        CustomerStats stats = new CustomerStats(
                orderRepository.countByCustomerId(id),
                returnRepository.countByCustomerId(id),
                ticketRepository.countByCustomerId(id),
                flagRepository.countBySubjectTypeAndSubjectIdAndActiveTrue(FlagSubjectType.CUSTOMER, id));
        return mapper.toCustomerDetail(user, addresses, stats);
    }

    public PageResponse<CustomerReviewDto> customerReviews(Long customerId, Pageable pageable) {
        requireCustomer(customerId);
        return PageResponse.of(reviewRepository.findByCustomerId(customerId, pageable),
                catalogMapper::toCustomerReview);
    }

    /** Non-customer users (staff and seller accounts), optionally filtered by role and seller. */
    public PageResponse<UserDto> searchUsers(Role role, Long sellerId, String q, Pageable pageable) {
        List<Role> roles = role != null ? List.of(role)
                : Arrays.stream(Role.values()).filter(r -> r != Role.CUSTOMER).toList();
        Specification<AppUser> spec = Specification.allOf(
                Specs.in("role", roles),
                Specs.eq("seller.id", sellerId),
                Specs.containsIgnoreCase(q, "fullName", "email"));
        return PageResponse.of(userRepository.findAll(spec, pageable), mapper::toUser);
    }

    private AppUser requireCustomer(Long id) {
        return userRepository.findById(id).filter(u -> u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new NotFoundException("Customer", id));
    }
}
