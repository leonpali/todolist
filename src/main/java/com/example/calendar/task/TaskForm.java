package com.example.calendar.task;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

public class TaskForm {

	@NotBlank
	@Size(max = 120)
	private String headline;

	@Size(max = 2000)
	private String description;

	@NotNull
	@DecimalMin("0.25")
	@DecimalMax("40")
	private Double estimatedHours = 1.0;

	@NotNull
	private TaskStatus status = TaskStatus.TODO;

	@NotNull
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate date;

	public String getHeadline() { return headline; }
	public void setHeadline(String headline) { this.headline = headline; }

	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }

	public Double getEstimatedHours() { return estimatedHours; }
	public void setEstimatedHours(Double estimatedHours) { this.estimatedHours = estimatedHours; }

	public TaskStatus getStatus() { return status; }
	public void setStatus(TaskStatus status) { this.status = status; }

	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }

	@AssertTrue(message = "Pick a weekday (Monday to Friday)")
	public boolean isOnWeekday() {
		return date == null || TaskService.isWeekday(date);
	}
}
