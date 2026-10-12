package com.personalsite.security;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, String> {

	void deleteByUsername(String username);

	long countByUsername(String username);

}
