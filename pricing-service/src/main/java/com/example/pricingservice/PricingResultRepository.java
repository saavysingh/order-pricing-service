package com.example.pricingservice;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingResultRepository extends JpaRepository<PricingResult, UUID> {
}
