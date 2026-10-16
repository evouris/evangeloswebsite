package com.personalsite.notes;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteFolderRepository extends JpaRepository<NoteFolder, Long> {

	List<NoteFolder> findAllByOrderByNameAsc();

	Optional<NoteFolder> findByNameIgnoreCase(String name);

}
