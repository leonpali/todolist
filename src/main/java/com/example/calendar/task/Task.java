package com.example.calendar.task;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.example.calendar.user.AppUser;

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

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_email", nullable = false, updatable = false)
	private AppUser owner;

	protected Task() {
	}

	public Task(AppUser owner, String headline, String description, double estimatedHours, TaskStatus status,
			LocalDate date, Instant now) {
		this.id = UUID.randomUUID();
		this.owner = owner;
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
	public AppUser getOwner() { return owner; }
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
