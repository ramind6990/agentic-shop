package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.domain.Address;

import java.util.List;

/** Saved addresses. */
public record AddressListBlock(List<Address> items) implements ReplyBlock {
}
