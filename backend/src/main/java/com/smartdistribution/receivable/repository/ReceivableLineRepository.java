package com.smartdistribution.receivable.repository;

import com.smartdistribution.receivable.entity.ReceivableLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceivableLineRepository extends JpaRepository<ReceivableLine, Long> {

    List<ReceivableLine> findAllByReceivableIdOrderByLineNoAsc(Long receivableId);
}
