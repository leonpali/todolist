package com.example.calendar.task;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "task")
public class Task {

	@Id
	private UUID id;

	@Column(nullable = false, length = 120)
	private String headline;

	@Column(nullable = false, length = 2000)
	private String description;

	@Column(nullable = false)
	private double estimatedHours;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TaskStatus status;

	@Column(name = "scheduled_date", nullable = false)
	private LocalDate date;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant completedAt;

	protected Task() {
	}

	public Task(String headline, String description, double estimatedHours, TaskStatus status, LocalDate date,
			Instant now) {
		this.id = UUID.randomUUID();
		this.headline = headline;
		this.description = description;
		this.estimatedHours = estimatedHours;
		this.date = date;
		this.createdAt = now;
		changeStatus(status, now);
	}

	public void changeStatus(TaskStatus newStatus, Instant now) {
		if (newStatus == TaskStatus.DONE && this.status != TaskStatus.DONE) {
			this.completedAt = now;
		}
		else if (newStatus != TaskStatus.DONE) {
			this.completedAt = null;
		}
		this.status = newStatus;
	}

	public void changeDate(LocalDate newDate) {
		this.date = newDate;
	}

	public UUID getId() { return id; }
	public String getHeadline() { return headline; }
	public String getDescription() { return description; }
	public double getEstimatedHours() { return estimatedHours; }
	public TaskStatus getStatus() { return status; }
	public LocalDate getDate() { return date; }
	public DayOfWeek getDay() { return date.getDayOfWeek(); }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getCompletedAt() { return completedAt; }

	public String getEstimateLabel() {
		long minutes = Math.round(estimatedHours * 60);
		long h = minutes / 60;
		long m = minutes % 60;
		if (h == 0) {
			return m + "m";
		}
		return m == 0 ? h + "h" : h + "h " + m + "m";
	}
}
