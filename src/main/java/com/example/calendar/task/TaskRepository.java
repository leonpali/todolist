package com.example.calendar.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, UUID> {

	List<Task> findByDateBetweenOrderByCreatedAtAsc(LocalDate from, LocalDate to);

	@Modifying
	@Query("delete from Task t where t.completedAt < :cutoff")
	int deleteCompletedBefore(Instant cutoff);
}
