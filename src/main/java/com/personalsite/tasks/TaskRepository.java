package com.personalsite.tasks;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

	List<Task> findAllByOrderByCompletedAscDueDateAscIdAsc();

}
