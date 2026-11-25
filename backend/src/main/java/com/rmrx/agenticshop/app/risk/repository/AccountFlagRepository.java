package com.rmrx.agenticshop.app.risk.repository;

import com.rmrx.agenticshop.app.risk.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AccountFlagRepository extends JpaRepository<AccountFlag, Long>, JpaSpecificationExecutor<AccountFlag> {

    long countBySubjectTypeAndSubjectIdAndActiveTrue(FlagSubjectType subjectType, Long subjectId);
}
