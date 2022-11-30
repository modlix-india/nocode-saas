package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.security.service.SecurityMessageResourceService.CLIENT_PASSWORD_POLICY_ERROR;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_UPDATE')")
	@Override
	protected Mono<ClientPasswordPolicy> updatableEntity(ClientPasswordPolicy entity) {
		return super.update(entity);
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		if (fields == null || key == null)
			return Mono.just(new HashMap<String, Object>());

		fields.remove("clientId");
		fields.remove("updatedAt");
		fields.remove("updatedBy");
		fields.remove("createdAt");
		fields.remove("createdBy");

		return Mono.just(fields);
	}

	public Mono<ClientPasswordPolicy> getPolicyByClientId(ULong clientId) {
		return this.dao.getByClientId(clientId);
	}

	public Mono<Boolean> checkAllConditions(ULong clientId, String password) {

		return flatMapMono(

		        () -> this.dao.getByClientId(clientId),

		        passwordPolicy -> passwordPolicy != null ? checkAlphanumericExists(passwordPolicy, password)
		                : Mono.empty(),

		        (passwordPolicy, isAlphaNumberic) -> passwordPolicy.isAtleastOneSpecialChar()
		                ? checkInSpecialCharacters(password).map(val -> val && isAlphaNumberic)
		                : Mono.just(isAlphaNumberic),

		        // re check here
		        (passwordPolicy, isAlphaNumberic, isSpecial) ->

				passwordPolicy.isSpacesAllowed() ? Mono.just(isAlphaNumberic && isSpecial)
				        : Mono.just(password.contains(" ") && isAlphaNumberic && isSpecial),

		        (passwordPolicy, isAlphaNumberic, isSpecial, isSpace) ->
				{
			        if (!isSpace.booleanValue())
				        return securityMessageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
				                SecurityMessageResourceService.SPACES_MISSING);

			        String regex = passwordPolicy.getRegex();
			        return regex != null && !regex.contains("")
			                ? checkRegexPattern(password, regex)
			                        .map(val -> val && isAlphaNumberic && isSpecial && isSpace)
			                : Mono.just(isAlphaNumberic && isSpecial && isSpace);

		        }, (passwordPolicy, isAlphaNumberic, isSpecial, isSpace, isRegex) -> {

			        if (!isRegex.booleanValue())
				        return securityMessageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
				                SecurityMessageResourceService.REGEX_MISMATCH);

			        return checkStrengthOfPassword(passwordPolicy, password);
		        },

		        (passwordPolicy, isAlphaNumberic, isSpecial, isSpace, isRegex, isLength) ->

				isLength.booleanValue() ? Mono.just(isAlphaNumberic && isSpecial && isSpace && isRegex) : Mono.empty(),

		        (passwordPolicy, isAlphaNumberic, isSpecial, isSpace, isRegex, isLength, isValid) -> Mono
		                .just(isAlphaNumberic && isSpecial && isSpace && isRegex && isValid)

		// Add past passwords and history check later in that past passwords service

		).switchIfEmpty(
		        securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN, CLIENT_PASSWORD_POLICY_ERROR));
	}

	private Mono<Boolean> checkAlphanumericExists(ClientPasswordPolicy passwordPolicy, String password) {

		return flatMapMono(

		        () -> passwordPolicy.isAtleastOneUppercase() ? checkExistsInBetween(password, 'A', 'Z')
		                : Mono.just(true),

		        isUpper ->
				{

			        if (!isUpper.booleanValue())
				        return securityMessageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
				                SecurityMessageResourceService.CAPTIAL_LETTERS_MISSING);

			        return passwordPolicy.isAtleastoneLowercase()
			                ? checkExistsInBetween(password, 'a', 'z').map(val -> val && isUpper)
			                : Mono.just(isUpper);
		        },

		        (isUpper, isLower) ->
				{

			        if (!isLower.booleanValue())
				        return securityMessageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
				                SecurityMessageResourceService.SMALL_LETTERS_MISSING);

			        return passwordPolicy.isAtleastOneDigit()
			                ? checkExistsInDigits(password, 48, 57).map(val -> val && isLower && isUpper)
			                : Mono.just(isUpper && isLower);
		        },

		        (isUpper, isLower, isNumeric) ->
				{
			        if (!isNumeric.booleanValue())
				        return securityMessageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
				                SecurityMessageResourceService.NUMBERS_MISSING);

			        return Mono.just(isUpper && isLower && isNumeric);

		        }

		);
	}

	private Mono<Boolean> checkExistsInDigits(String password, int minBoundary, int maxBoundary) {

		for (int i = 0; i < password.length(); i++) {

			int ch = password.charAt(i);

			if (ch >= minBoundary && ch <= maxBoundary)
				return Mono.just(true);

		}
		return Mono.just(false);
	}

	private Mono<Boolean> checkExistsInBetween(String password, char minBoundary, char maxBoundary) {

		for (int i = 0; i < password.length(); i++) {

			char ch = password.charAt(i);

			if (ch >= minBoundary && ch <= maxBoundary)
				return Mono.just(true);

		}

		return Mono.just(false);
	}

	private Mono<Boolean> checkStrengthOfPassword(ClientPasswordPolicy passwordPolicy, String password) {

		boolean isValid = true;

		if (passwordPolicy.getPassMaxLength() != null && passwordPolicy.getPassMinLength() != null) {

			if (password.length() > passwordPolicy.getPassMaxLength()
			        .intValue())
				return securityMessageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
				        SecurityMessageResourceService.MAX_LENGTH_ERROR, passwordPolicy.getPassMaxLength());

			if (password.length() < passwordPolicy.getPassMinLength()
			        .intValue())
				return securityMessageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
				        SecurityMessageResourceService.MIN_LENGTH_ERROR, passwordPolicy.getPassMinLength());

			isValid = password.length() <= passwordPolicy.getPassMaxLength()
			        .intValue()
			        && password.length() >= passwordPolicy.getPassMinLength()
			                .intValue();
		}

		return Mono.just(isValid);

	}

	private Mono<Boolean> checkInSpecialCharacters(String password) {

		for (int i = 0; i < password.length(); i++) {
			Character ch = password.charAt(i);
			if (specialCharacters.contains(ch))
				return Mono.just(true);
		}

		return securityMessageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
		        SecurityMessageResourceService.SPECIAL_CHARACTERS_MISSING);
	}

	private Mono<Boolean> checkRegexPattern(String password, String regex) {

		Pattern pattern = Pattern.compile(regex);
		Matcher matches = pattern.matcher(password);
		return Mono.just(matches.find());
	}

}
