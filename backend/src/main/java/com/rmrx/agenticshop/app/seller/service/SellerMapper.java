package com.rmrx.agenticshop.app.seller.service;

import com.rmrx.agenticshop.app.seller.dto.SellerDtos.*;
import com.rmrx.agenticshop.app.seller.entity.FeePlan;
import com.rmrx.agenticshop.app.seller.entity.Payout;
import com.rmrx.agenticshop.app.seller.entity.Seller;
import com.rmrx.agenticshop.app.seller.entity.SellerContract;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface SellerMapper {

    SellerSummary toSummary(Seller seller);

    SellerDetail toDetail(Seller seller, FeePlan currentFeePlan, SellerStats stats);

    @Mapping(target = "documentCode", source = "document.code")
    ContractDto toContract(SellerContract contract);

    PayoutDto toPayout(Payout payout);
}
