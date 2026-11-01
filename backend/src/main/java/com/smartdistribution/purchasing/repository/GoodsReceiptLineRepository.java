package com.smartdistribution.purchasing.repository;

import com.smartdistribution.purchasing.entity.GoodsReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, Long> {

    List<GoodsReceiptLine> findAllByGoodsReceiptIdOrderByIdAsc(Long goodsReceiptId);
}
