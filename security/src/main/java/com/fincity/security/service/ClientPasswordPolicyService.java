package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.security.service.SecurityMessageResourceService.CLIENT_PASSWORD_POLICY_ERROR;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.security.dao.ClientPasswordPolicyDAO;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;

import reactor.core.publisher.Mono;

@Service
public class ClientPasswordPolicyService extends
        AbstractJOOQUpdatableDataService<SecurityClientPasswordPolicyRecord, ULong, ClientPasswordPolicy, ClientPasswordPolicyDAO> {

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	private final Set<Character> specialCharacters = new HashSet<>(
	        Arrays.asList('~', '`', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '_', '-', '+', '=', '{', '}', '[',
	                ']', '|', '\\', '/', ':', ';', '\"', '\'', '<', '>', ',', '.', '?'));

	@Override
	protected Mono<ClientPasswordPolicy> updatableEntity(ClientPasswordPolicy entity) {

		return null;
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		return null;
	}

	public Mono<ClientPasswordPolicy> getPasswordPolicyByClientId(ULong clientId) {
		return this.dao.getByClientId(clientId);
	}

	public Mono<Boolean> checkAllConditions(ULong clientId, String password) {

		return flatMapMono(

		        () -> this.getPasswordPolicyByClientId(clientId),

		        passwordPolicy -> passwordPolicy != null ? checkAlphanumericExists(passwordPolicy, password)
		                : Mono.empty(),

		        (passwordPolicy, isAlphaNumberic) -> passwordPolicy.isAtleastOneSpecialChar()
		                ? checkInSpecialCharacters(password).map(val -> val && isAlphaNumberic)
		                : Mono.just(isAlphaNumberic),

		        (passwordPolicy, isAlphaNumberic, isSpecial) -> passwordPolicy.isSpacesAllowed()
		                ? Mono.just(password.contains(" "))
		                        .map(val -> val && isAlphaNumberic && isSpecial)
		                : Mono.just(isAlphaNumberic && isSpecial),

		        (passwordPolicy, isAlphaNumberic, isSpecial, isSpace) ->
				{

			        String regex = passwordPolicy.getRegex();
			        return regex != null && !regex.contains("")
			                ? checkRegexPattern(password, regex)
			                        .map(val -> val && isAlphaNumberic && isSpecial && isSpace)
			                : Mono.just(isAlphaNumberic && isSpecial && isSpace);

		        },
		        (passwordPolicy, isAlphaNumberic, isSpecial, isSpace,
		                isRegex) -> checkStrengthOfPassword(passwordPolicy, password),

		        (passwordPolicy, isAlphaNumberic, isSpecial, isSpace, isRegex, isLength) ->

				isLength.booleanValue() ? Mono.just(isAlphaNumberic && isSpecial && isSpace && isRegex) : Mono.empty(),

		        (passwordPolicy, isAlphaNumberic, isSpecial, isSpace, isRegex, isLength, isValid) -> Mono
		                .just(isAlphaNumberic && isSpecial && isSpace && isRegex && isValid)

		// Add past passwords and history check later in that past passwords service

		).switchIfEmpty(
		        securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN, CLIENT_PASSWORD_POLICY_ERROR));
	}

	public Mono<Boolean> checkAlphanumericExists(ClientPasswordPolicy passwordPolicy, String password) {

		return flatMapMono(

		        () -> passwordPolicy.isAtleastOneUppercase() ? checkExistsInBetween(password, 65, 90) : Mono.just(true),

		        isUpper -> passwordPolicy.isAtleastoneLowercase()
		                ? checkExistsInBetween(password, 96, 122).map(val -> val && isUpper)
		                : Mono.just(isUpper),

		        (isUpper, isLower) -> passwordPolicy.isAtleastOneDigit()
		                ? checkExistsInBetween(password, 48, 57).map(val -> val && isLower && isUpper)
		                : Mono.just(isUpper && isLower)

		);
	}

	public Mono<Boolean> checkExistsInBetween(String password, int minBoundary, int maxBoundary) {

		for (int i = 0; i < password.length(); i++) {

			int ch = password.charAt(i);
			System.out.print(ch + " ");
			if (ch >= minBoundary && ch <= maxBoundary)
				return Mono.just(true);

		}
		System.out.println();
		return Mono.just(false);
	}

	public Mono<Boolean> checkStrengthOfPassword(ClientPasswordPolicy passwordPolicy, String password) {

		boolean isValid = true;

		if (passwordPolicy.getPassMaxLength() != null && passwordPolicy.getPassMinLength() != null)
			isValid = password.length() <= passwordPolicy.getPassMaxLength()
			        .intValue()
			        && password.length() >= passwordPolicy.getPassMinLength()
			                .intValue();

		System.out.println(isValid + " from length of password ");

		return Mono.just(isValid);

	}

	public Mono<Boolean> checkInSpecialCharacters(String password) {

		for (int i = 0; i < password.length(); i++) {
			Character ch = password.charAt(i);
			if (specialCharacters.contains(ch))
				return Mono.just(true);
		}
		return Mono.empty();
	}

	public Mono<Boolean> checkRegexPattern(String password, String regex) {

		Pattern pattern = Pattern.compile(regex);
		Matcher matches = pattern.matcher(password);
		return Mono.just(matches.find());
	}
}
