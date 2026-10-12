package com.personalsite.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

	List<Task> findByDueDateBetween(LocalDate start, LocalDate end);

	Optional<Task> findByExternalId(String externalId);

}
