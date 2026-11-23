package com.rmrx.agenticshop.common.web;

import com.rmrx.agenticshop.catalog.entity.AttributeDataType;
import com.rmrx.agenticshop.catalog.entity.ProductStatus;
import com.rmrx.agenticshop.docs.entity.DocumentType;
import com.rmrx.agenticshop.identity.entity.Role;
import com.rmrx.agenticshop.identity.entity.UserStatus;
import com.rmrx.agenticshop.order.entity.*;
import com.rmrx.agenticshop.returns.entity.RefundStatus;
import com.rmrx.agenticshop.returns.entity.ReturnReason;
import com.rmrx.agenticshop.returns.entity.ReturnStatus;
import com.rmrx.agenticshop.risk.entity.FlagSubjectType;
import com.rmrx.agenticshop.risk.entity.FlagType;
import com.rmrx.agenticshop.seller.entity.FeePlan;
import com.rmrx.agenticshop.seller.entity.PayoutStatus;
import com.rmrx.agenticshop.seller.entity.SellerStatus;
import com.rmrx.agenticshop.support.entity.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lists the values of every enum used in the API, so the UI can build filter dropdowns. */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private static final List<Class<? extends Enum<?>>> ENUMS = List.of(
            Role.class, UserStatus.class, SellerStatus.class, FeePlan.class, PayoutStatus.class,
            ProductStatus.class, AttributeDataType.class, OrderStatus.class, PaymentMethod.class,
            PaymentStatus.class, Carrier.class, ShipmentStatus.class, ReturnReason.class, ReturnStatus.class,
            RefundStatus.class, TicketCategory.class, TicketStatus.class, TicketPriority.class, SupportTier.class,
            AuthorType.class, FlagSubjectType.class, FlagType.class, DocumentType.class);

    @GetMapping("/enums")
    public Map<String, List<String>> enums() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Class<? extends Enum<?>> e : ENUMS) {
            result.put(e.getSimpleName(), Arrays.stream(e.getEnumConstants()).map(Enum::name).toList());
        }
        return result;
    }
}
