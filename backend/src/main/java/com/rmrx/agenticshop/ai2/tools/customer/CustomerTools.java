package com.rmrx.agenticshop.ai2.tools.customer;

import com.rmrx.agenticshop.ai2.contract.tool.response.customer.AddressListResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.customer.CustomerDetailResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.customer.CustomerListResponse;
import com.rmrx.agenticshop.ai2.tools.AgentToolSet;
import com.rmrx.agenticshop.ai2.tools.Audience;
import com.rmrx.agenticshop.ai2.tools.ToolAudience;
import com.rmrx.agenticshop.ai2.tools.ToolLabel;
import com.rmrx.agenticshop.ai2.tools.ToolPaging;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.identity.dto.UserDtos.CustomerSummary;
import com.rmrx.agenticshop.app.identity.entity.UserStatus;
import com.rmrx.agenticshop.app.identity.service.AddressService;
import com.rmrx.agenticshop.app.identity.service.UserService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Customers: saved addresses (customer variant: their own; staff variant: any customer's), and, for staff, finding a
 * customer and their profile.
 */
@Component
public class CustomerTools implements AgentToolSet {

    public static final String GET_ADDRESSES = "get_addresses";
    public static final String FIND_CUSTOMERS = "find_customers";
    public static final String GET_CUSTOMER = "get_customer";

    private final AddressService addresses;
    private final UserService users;
    private final CustomerToolMapper mapper;

    public CustomerTools(AddressService addresses, UserService users, CustomerToolMapper mapper) {
        this.addresses = addresses;
        this.users = users;
        this.mapper = mapper;
    }

    @ToolAudience(Audience.CUSTOMER)
    @ToolLabel("Looking up your saved addresses")
    @Tool(name = GET_ADDRESSES, value = "The customer's saved addresses (id, label, city, default first), e.g. to "
            + "choose where an order should go instead.")
    public AddressListResponse getAddresses() {
        return addressesOf(null);
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Looking up saved addresses")
    @Tool(name = GET_ADDRESSES, value = "A customer's saved addresses (id, label, city, default first).")
    public AddressListResponse getAddressesForStaff(@P("Customer id from find_customers") Long customerId) {
        return addressesOf(customerId);
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Looking for customer {query}")
    @Tool(name = FIND_CUSTOMERS, value = "Find active customers whose name or email contains the text: a page of 10 "
            + "with their ids. Use it first when helping a specific customer.")
    public CustomerListResponse findCustomers(
            @P("Part of the customer's name or email") String query,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Give part of a name or email.");
        PageResponse<CustomerSummary> found = users.searchCustomers(query, UserStatus.ACTIVE,
                ToolPaging.pageable(page, Sort.by("fullName", "id")));
        return new CustomerListResponse(found.content().stream().map(mapper::toCustomer).toList(),
                ToolPaging.info(found));
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Looking up customer {customerId}")
    @Tool(name = GET_CUSTOMER, value = "One customer's profile: contact details, saved addresses and how many orders, "
            + "returns and support tickets they have.")
    public CustomerDetailResponse getCustomer(@P("Customer id from find_customers") Long customerId) {
        return mapper.toDetail(users.getCustomer(customerId));
    }

    private AddressListResponse addressesOf(Long customerId) {
        return new AddressListResponse(customerId,
                addresses.addresses(customerId).stream().map(mapper::toAddress).toList());
    }
}
