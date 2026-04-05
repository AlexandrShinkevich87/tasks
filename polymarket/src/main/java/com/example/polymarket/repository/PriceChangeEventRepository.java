package com.example.polymarket.repository;

import com.example.polymarket.entity.PriceChangeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceChangeEventRepository extends JpaRepository<PriceChangeEventEntity, Long> {
}