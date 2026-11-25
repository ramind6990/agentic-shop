package com.rmrx.agenticshop.app.order.web;

import org.springframework.security.access.prepost.PreAuthorize;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.web.Paging;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderDetail;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderSummary;
import com.rmrx.agenticshop.app.order.entity.OrderStatus;
import com.rmrx.agenticshop.app.order.service.OrderService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@PreAuthorize("hasAuthority('ORDERS_READ')")
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<OrderSummary> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) OrderStatus status,
                                             @RequestParam(required = false) Long customerId,
                                             @RequestParam(required = false) Long sellerId,
                                             @RequestParam(required = false) Instant from,
                                             @RequestParam(required = false) Instant to,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return orderService.search(q, status, customerId, sellerId, from, to,
                Paging.of(page, size, Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id"))));
    }

    @GetMapping("/{idOrNumber}")
    public OrderDetail get(@PathVariable String idOrNumber) {
        return orderService.get(idOrNumber);
    }
}
