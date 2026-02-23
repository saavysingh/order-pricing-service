package com.example.orderservice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
	@Query(value = "select * from outbox_events " +
			"where status in ('NEW','FAILED') and next_attempt_at <= :now " +
			"order by created_at asc " +
			"limit :limit for update skip locked",
			nativeQuery = true)
	List<OutboxEvent> findDueForPublish(@Param("now") Instant now, @Param("limit") int limit);

	Optional<OutboxEvent> findTopByAggregateIdAndEventTypeOrderByCreatedAtDesc(UUID aggregateId, String eventType);
}
