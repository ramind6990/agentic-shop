package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import com.rmrx.agenticshop.app.identity.dto.UserDtos.AddressDto;
import com.rmrx.agenticshop.app.identity.service.AddressService;

import java.util.Comparator;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** {@link AddressService#addresses}: the customer's saved addresses, default first. */
final class AddressStubs {

    private final WorldContext ctx;

    AddressStubs(WorldContext ctx) {
        this.ctx = ctx;
    }

    void install(AddressService addresses) {
        when(addresses.addresses(any())).thenAnswer(inv -> {
            ctx.enter("AddressService.addresses");
            return addresses(inv.getArgument(0));
        });
    }

    private List<AddressDto> addresses(Long requested) {
        Long owner = ctx.visibility().customerFilter(requested);
        if (owner == null) throw new BadRequestException("Which customer? A customerId is required.");
        return ctx.world().addresses.stream()
                .filter(a -> a.ownerId.equals(owner))
                .sorted(Comparator.comparing((WorldAddress a) -> !a.isDefault).thenComparing(a -> a.id))
                .map(a -> new AddressDto(a.id, a.label, a.recipient, a.line1, a.line2, a.city, a.region,
                        a.postalCode, a.country, a.isDefault))
                .toList();
    }
}
