package com.personalsite.security;

import java.util.Date;

import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores remembered devices in our own database table, so they survive restarts and redeploys. */
@Component
@Transactional
public class JpaPersistentTokenRepository implements PersistentTokenRepository {

	private final RememberMeTokenRepository tokens;

	public JpaPersistentTokenRepository(RememberMeTokenRepository tokens) {
		this.tokens = tokens;
	}

	@Override
	public void createNewToken(PersistentRememberMeToken token) {
		tokens.save(new RememberMeToken(token.getSeries(), token.getUsername(), token.getTokenValue(),
				token.getDate().toInstant()));
	}

	@Override
	public void updateToken(String series, String tokenValue, Date lastUsed) {
		tokens.findById(series).ifPresent(token -> {
			token.setTokenValue(tokenValue);
			token.setLastUsed(lastUsed.toInstant());
		});
	}

	@Override
	@Transactional(readOnly = true)
	public PersistentRememberMeToken getTokenForSeries(String series) {
		return tokens.findById(series)
			.map(token -> new PersistentRememberMeToken(token.getUsername(), token.getSeries(), token.getTokenValue(),
					Date.from(token.getLastUsed())))
			.orElse(null);
	}

	/** Forgets every device for this user ("sign out all devices"). */
	@Override
	public void removeUserTokens(String username) {
		tokens.deleteByUsername(username);
	}

	/** Forgets just one device. */
	public void removeSeries(String series) {
		tokens.deleteById(series);
	}

}
