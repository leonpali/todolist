package com.example.calendar.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, UUID> {

	List<Task> findByOwner_EmailAndDateBetweenOrderByCreatedAtAsc(String ownerEmail, LocalDate from, LocalDate to);

	Optional<Task> findByIdAndOwner_Email(UUID id, String ownerEmail);

	List<Task> findByOwner_Email(String ownerEmail);

	@Modifying
	@Query("delete from Task t where t.completedAt < :cutoff")
	int deleteCompletedBefore(Instant cutoff);
}
