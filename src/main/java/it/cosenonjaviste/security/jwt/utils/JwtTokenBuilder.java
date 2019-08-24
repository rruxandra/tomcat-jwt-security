package it.cosenonjaviste.security.jwt.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.auth0.jwt.impl.PublicClaims.*;


/**
 * Builder class for simplifying JWT token creation.
 * 
 * <tt>userId</tt> and <tt>roles</tt> values are mandatory.
 * 
 * @author acomo
 *
 */
public class JwtTokenBuilder {


	private JWTCreator.Builder jwtBuilder = JWT.create();

	private ClaimsAdapter claims = new ClaimsAdapter(jwtBuilder);

	private OptionsAdapter optionsAdapter = new OptionsAdapter(jwtBuilder);

	private Algorithm algorithm;

	private JwtTokenBuilder() {
		
	}
	
	/**
	 * Creates a new {@link JwtTokenBuilder} instance
	 * 
	 * @param secret secret phrase
	 * 
	 * @return a new {@link JwtTokenBuilder} instance
	 */
	public static JwtTokenBuilder create(String secret) {
		JwtTokenBuilder builder = new JwtTokenBuilder();
		builder.algorithm = Algorithm.HMAC256(secret);
		builder.optionsAdapter.setIssuedAt(true);

		return builder;
	}
	
	/**
	 * Creates a {@link JwtTokenBuilder} instance from token and secret.
	 * <br >
	 * Token will be <strong>validated</strong> before parsing.
	 * <br >
	 * Token <strong>must</strong> contains "<em>iat</em>" param in order to restore builder status
	 * <br >
	 * <br >
	 * Rebuilding this token has side effect:
	 * <ul>
	 * <li>if "<em>jti</em>" param is present, will be overwritten</li> 
	 * <li>if "<em>exp</em>" param is present, expire time will be recalculated starting from current timestamp</li> 
	 * <li>if "<em>nbf</em>" param is present, its value will be recalculated starting from current timestamp</li> 
	 * </ul>
	 * 
	 * @param token
	 * @param secret
	 * @return
	 */
	public static JwtTokenBuilder from(String token, String secret) {
		JwtTokenVerifier verifier = JwtTokenVerifier.create(secret);
		verifier.verify(token);
		return from(verifier, secret);
	}

	/**
	 * Creates a {@link JwtTokenBuilder} instance from token and secret.
	 * <br >
	 * Token <strong>must</strong> contains "<em>iat</em>" param in order to restore builder status
	 * <br >
	 * Use this method if you want to edit current token: if "<em>jti</em>" param is present, will be overwritten 
	 * <br >
	 * Token <strong>must</strong> be verified before calling this method
	 * <br >
	 * <br >
	 * Rebuilding this token has side effect:
	 * <ul>
	 * <li>if "<em>jti</em>" param is present, will be overwritten</li> 
	 * <li>if "<em>exp</em>" param is present, expire time will be recalculated starting from current timestamp</li> 
	 * <li>if "<em>nbf</em>" param is present, its value will be recalculated starting from current timestamp</li> 
	 * </ul>
	 * 
	 * @param verifier
	 * @param secret
	 * @return
	 * 
	 * @throws IllegalStateException if token is not verified by provided verifier
	 */
	public static JwtTokenBuilder from(JwtTokenVerifier verifier, String secret) {
		JwtTokenBuilder builder = create(secret);
		DecodedJWT decodedJWT = verifier.getDecodedJWT();
		restoreInternalStatus(builder, decodedJWT);
		return builder;
	}

	/**
	 * Creates a {@link JwtTokenBuilder} instance from token and secret.
	 * <br >
	 * Token will be <strong>validated</strong> before parsing.
	 * <br >
	 * Token <strong>must</strong> contains "<em>iat</em>" param in order to restore builder status
	 * <br >
	 * Use this method if you want to edit current token: if "<em>jti</em>" param is present, will be overwritten 
	 * <br >
	 * <br >
	 * Rebuilding this token has side effect:
	 * <ul>
	 * <li>if "<em>jti</em>" param is present, will be overwritten</li> 
	 * <li>if "<em>exp</em>" param is present, expire time will be recalculated starting from current timestamp</li> 
	 * <li>if "<em>nbf</em>" param is present, its value will be recalculated starting from current timestamp</li> 
	 * </ul>
	 * @param verifier
	 * @param token
	 * @param secret
	 * @return
	 * 
	 * @throws IllegalStateException if token is not verified by provided verifier
	 */
	public static JwtTokenBuilder from(JwtTokenVerifier verifier, String token, String secret) {
		verifier.verify(token);
		return from(verifier, secret);
	}
	
	private static void restoreInternalStatus(JwtTokenBuilder builder, DecodedJWT decodedJWT) {
		Map<String, Claim> verifiedClaims = new HashMap<>(decodedJWT.getClaims());
		if (verifiedClaims.containsKey(ISSUED_AT)) {
			int issuedAt = verifiedClaims.remove(ISSUED_AT).asInt();
			if (verifiedClaims.containsKey(EXPIRES_AT)) {
				int expire = verifiedClaims.remove(EXPIRES_AT).asInt() - issuedAt;
				builder.optionsAdapter.setExpirySeconds(expire);
			}
			if (verifiedClaims.containsKey(NOT_BEFORE)) {
				int notBefore = issuedAt - verifiedClaims.remove(NOT_BEFORE).asInt();
				builder.optionsAdapter.setNotValidBeforeLeeway(notBefore);
			}
			if (verifiedClaims.containsKey(JWT_ID)) {
				verifiedClaims.remove(JWT_ID);
				builder.optionsAdapter.setJwtId(true);
			}
			builder.claims.putAll(verifiedClaims);
		} else {
			throw new IllegalStateException("Missing 'iat' value. Unable to restore builder status");
		}
	}

	/**
	 * Add <tt>userId</tt> claim to JWT body
	 * 
	 * @param name realm username
	 * 
	 * @return {@link JwtTokenBuilder}
	 */
	public JwtTokenBuilder userId(String name) {
		claims.put(JwtConstants.USER_ID, name);

		return this;
	}
	
	/**
	 * Add <tt>roles</tt> claim to JWT body
	 * 
	 * @param roles
	 * 
	 * @return {@link JwtTokenBuilder}
	 */
	public JwtTokenBuilder roles(Collection<String> roles) {
		claims.put(JwtConstants.ROLES, roles.toArray(new String[]{}));

		return this;
	}
	
	/**
	 * Add a custom claim to JWT body
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return {@link JwtTokenBuilder}
	 */
	public JwtTokenBuilder claimEntry(String key, Object value) {
		claims.put(key, value);
		return this;
	}
	
	/**
	 * Add JWT claim <tt>exp</tt> to current timestamp + seconds.
	 * 
	 * @param seconds
	 * 
	 * @return {@link JwtTokenBuilder}
	 */
	public JwtTokenBuilder expirySecs(int seconds) {
		optionsAdapter.setExpirySeconds(seconds);
		return this;
	}
	
	/**
	 * Specify algorithm to sign JWT with. Default is HS256.
	 * 
	 * @param algorithm
	 * 
	 * @return {@link JwtTokenBuilder}
	 */
	public JwtTokenBuilder algorithm(Algorithm algorithm) {
		this.algorithm = algorithm;
		return this;
	}
	
	/**
	 * Should JWT claim <tt>iat</tt> be added?
	 * Value will be set to current timestamp
	 * 
	 * @param issuedAt
	 * 
	 * @return {@link JwtTokenBuilder}
	 */
	public JwtTokenBuilder issuedEntry(boolean issuedAt) {
		optionsAdapter.setIssuedAt(issuedAt);
		return this;
	}
	
	/**
	 * Should JWT claim <tt>jti</tt> be added?
	 * Value will be set to a pseudo random unique value (UUID)
	 * 
	 * @param jwtId
	 * 
	 * @return {@link JwtTokenBuilder}
	 */
	public JwtTokenBuilder generateJwtId(boolean jwtId) {
		optionsAdapter.setJwtId(jwtId);
		return this;
	}
	
	/**
	 * Add JWT claim <tt>nbf</tt> to current timestamp - notValidBeforeLeeway
	 * 
	 * @param notValidBeforeLeeway
	 * 
	 * @return {@link JwtTokenBuilder}
	 */
	public JwtTokenBuilder notValidBeforeLeeway(int notValidBeforeLeeway) {
		optionsAdapter.setNotValidBeforeLeeway(notValidBeforeLeeway);
		return this;
	}
	
	/**
	 * Create a new JWT token
	 * 
	 * @return JWT token
	 * 
	 * @throws IllegalStateException if <tt>userId</tt> and <tt>roles</tt> are not provided
	 */
	public String build() {
		Preconditions.checkState(claims.containsKey(JwtConstants.USER_ID) && claims.containsKey(JwtConstants.ROLES), "userId and roles claims must be added!");
		return jwtBuilder.sign(algorithm);
	}
}
