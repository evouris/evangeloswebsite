package com.personalsite.notes;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NoteRepository extends JpaRepository<Note, Long> {

	List<Note> findAllByOrderByPinnedDescUpdatedAtDesc();

	long countByFolder(NoteFolder folder);

	/** When a folder is deleted its notes become unfiled rather than disappearing. */
	@Modifying
	@Transactional
	@Query("update Note n set n.folder = null where n.folder = :folder")
	void unfileAll(NoteFolder folder);

}
