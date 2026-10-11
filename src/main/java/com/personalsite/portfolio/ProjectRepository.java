package com.personalsite.portfolio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	List<Project> findAllByOrderBySortOrderAscIdAsc();

	List<Project> findByFeaturedTrueOrderBySortOrderAscIdAsc();

}
