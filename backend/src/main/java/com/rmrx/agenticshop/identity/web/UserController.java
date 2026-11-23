package com.rmrx.agenticshop.identity.web;

import com.rmrx.agenticshop.catalog.dto.ProductDtos.CustomerReviewDto;
import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.web.Paging;
import com.rmrx.agenticshop.identity.dto.UserDtos.CustomerDetail;
import com.rmrx.agenticshop.identity.dto.UserDtos.CustomerSummary;
import com.rmrx.agenticshop.identity.dto.UserDtos.UserDto;
import com.rmrx.agenticshop.identity.entity.Role;
import com.rmrx.agenticshop.identity.entity.UserStatus;
import com.rmrx.agenticshop.identity.service.UserService;
import com.rmrx.agenticshop.order.dto.OrderDtos.OrderSummary;
import com.rmrx.agenticshop.order.service.OrderService;
import com.rmrx.agenticshop.support.dto.TicketDtos.TicketSummary;
import com.rmrx.agenticshop.support.service.TicketService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final OrderService orderService;
    private final TicketService ticketService;

    public UserController(UserService userService, OrderService orderService, TicketService ticketService) {
        this.userService = userService;
        this.orderService = orderService;
        this.ticketService = ticketService;
    }

    @GetMapping("/customers")
    public PageResponse<CustomerSummary> customers(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) UserStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return userService.searchCustomers(q, status, Paging.of(page, size, Sort.by("fullName", "id")));
    }

    @GetMapping("/customers/{id}")
    public CustomerDetail customer(@PathVariable Long id) {
        return userService.getCustomer(id);
    }

    @GetMapping("/customers/{id}/orders")
    public PageResponse<OrderSummary> customerOrders(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        userService.getCustomer(id);
        return orderService.search(null, null, id, null, null, null,
                Paging.of(page, size, Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id"))));
    }

    @GetMapping("/customers/{id}/tickets")
    public PageResponse<TicketSummary> customerTickets(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        userService.getCustomer(id);
        return ticketService.search(null, null, null, null, null, id, null, null,
                Paging.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
    }

    @GetMapping("/customers/{id}/reviews")
    public PageResponse<CustomerReviewDto> customerReviews(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return userService.customerReviews(id,
                Paging.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    /** Staff and seller accounts with their roles. */
    @GetMapping("/users")
    public PageResponse<UserDto> users(@RequestParam(required = false) Role role,
                                       @RequestParam(required = false) Long sellerId,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return userService.searchUsers(role, sellerId, q, Paging.of(page, size, Sort.by("role", "fullName")));
    }
}
