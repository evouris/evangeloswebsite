package com.personalsite.tasks;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "tasks")
public class Task {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	private String title;

	private boolean completed;

	private LocalDate dueDate;

	@Column(length = 2000)
	private String notes;

	@Enumerated(EnumType.STRING)
	private Priority priority = Priority.MEDIUM;

	/** UID of the calendar event this task was imported from (e.g. a Canvas assignment), or null. */
	private String externalId;

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	/** Tasks saved before priorities existed have no value in the database; treat them as MEDIUM. */
	public Priority getPriority() {
		return priority == null ? Priority.MEDIUM : priority;
	}

	public void setPriority(Priority priority) {
		this.priority = priority;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public boolean isImported() {
		return externalId != null;
	}

	public boolean isOverdue() {
		return !completed && dueDate != null && dueDate.isBefore(LocalDate.now());
	}

}
