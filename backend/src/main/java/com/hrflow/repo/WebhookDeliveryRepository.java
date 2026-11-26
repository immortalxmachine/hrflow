package com.hrflow.repo;

import com.hrflow.domain.DeliveryStatus;
import com.hrflow.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findByStatus(DeliveryStatus status);

    long countByStatus(DeliveryStatus status);
}
