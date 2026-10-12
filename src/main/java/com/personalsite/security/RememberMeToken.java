package com.personalsite.security;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One remembered device. The cookie on that device holds the series plus a token that changes on every use. */
@Entity
@Table(name = "remember_me_tokens")
public class RememberMeToken {

	@Id
	private String series;

	private String username;

	private String tokenValue;

	private Instant lastUsed;

	protected RememberMeToken() {
	}

	public RememberMeToken(String series, String username, String tokenValue, Instant lastUsed) {
		this.series = series;
		this.username = username;
		this.tokenValue = tokenValue;
		this.lastUsed = lastUsed;
	}

	public String getSeries() {
		return series;
	}

	public String getUsername() {
		return username;
	}

	public String getTokenValue() {
		return tokenValue;
	}

	public void setTokenValue(String tokenValue) {
		this.tokenValue = tokenValue;
	}

	public Instant getLastUsed() {
		return lastUsed;
	}

	public void setLastUsed(Instant lastUsed) {
		this.lastUsed = lastUsed;
	}

}
