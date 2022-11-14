package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

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
//		ClientPasswordPolicy cpp = new ClientPasswordPolicy();
//		cpp.setAtleastOneDigit(true);
//		cpp.setAtleastoneLowercase(true);
//		cpp.setAtleastOneUppercase(true);
//		return Mono.just(cpp);
		return this.dao.getByClientId(clientId);
	}

	public Mono<Boolean> checkAllConditions(ULong clientId, String password) {

		return flatMapMono(

		        () -> this.getPasswordPolicyByClientId(clientId),

		        passwordPolicy -> checkAlphanumericExists(passwordPolicy, password),

		        (passwordPolicy, isAlphaNumberic) ->
				{
			        System.out.println("strength of password");
			        return checkStrengthOfPassword(passwordPolicy, password);
		        }

		        ,

		        (passwordPolicy, isAlphaNumberic, length) -> passwordPolicy.isAtleastOneSpecialChar()
		                ? checkInSpecialCharacters(password)
		                : Mono.just(true),

		        (passwordPolicy, isAlphaNumberic, length, isSpecial) -> passwordPolicy.isSpacesAllowed()
		                ? Mono.just(password.contains(" "))
		                : Mono.just(true),

		        (passwordPolicy, isAlphaNumberic, length, isSpecial, isSpace) ->
				{

			        System.out.println("from regex last step");
			        if (passwordPolicy.getRegex() != null && !passwordPolicy.getRegex()
			                .equals(""))
				        return this.checkRegexPattern(password, passwordPolicy.getRegex());
			        return Mono.just(true);
		        }

//		        (passwordPolicy, isAlphaNumberic, length, isSpecial, isSpace,
//		                isRegex) -> passwordPolicy.getPassHistoryCount() != null && passwordPolicy.getPassHistoryCount()
//		                        .intValue() > 0 ? this.dao.getPastPasswords(passwordPolicy.getClientId())
//		                                : Mono.empty(),
//
//		        (passwordPolicy, isAlphaNumberic, length, isSpecial, isSpace, isRegex, pastPasswords) ->
//				{
//			        System.out.println(pastPasswords);
//			        System.out.println("from last step of check password policy");
//
//			        if (pastPasswords != null)
//
//				        return Mono.just(pastPasswords.contains(password));
//			        return Mono.just(false); // recheck it later
//		        }

		// also check for % name condition

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        "Password cannot be updated as it is violating the rules defined by the client policy"));
	}

	public Mono<Boolean> checkAlphanumericExists(ClientPasswordPolicy passwordPolicy, String password) {

		return flatMapMono(

		        () -> passwordPolicy.isAtleastOneUppercase() ? checkExistsInBetween(password, 'A', 'Z')
		                : Mono.just(true),

		        isUpper -> passwordPolicy.isAtleastoneLowercase() ? checkExistsInBetween(password, 'a', 'z')
		                : Mono.just(true),

		        (isUpper, isLower) -> passwordPolicy.isAtleastOneDigit() ? checkExistsInBetween(password, 0, 9)
		                : Mono.just(true)

		);
	}

	public Mono<Boolean> checkExistsInBetween(String password, int minBoundary, int maxBoundary) {

		for (int i = 0; i < password.length(); i++) {

			int ch = password.charAt(i);
			if (ch >= minBoundary && ch <= maxBoundary)
				return Mono.just(true);

		}
		return Mono.just(false);
	}

	public Mono<Boolean> checkStrengthOfPassword(ClientPasswordPolicy passwordPolicy, String password) {

		boolean isValid = false;

		if (passwordPolicy.getPassMaxLength() != null)
			isValid = password.length() <= passwordPolicy.getPassMaxLength()
			        .intValue();

		if (passwordPolicy.getPassMinLength() != null)
			isValid = isValid && password.length() <= passwordPolicy.getPassMinLength()
			        .intValue();

		return Mono.just(isValid);

	}

	public Mono<Boolean> checkInSpecialCharacters(String password) {

		for (int i = 0; i < password.length(); i++) {
			Character ch = password.charAt(i);
			if (specialCharacters.contains(ch))
				return Mono.just(true);
		}
		return Mono.just(false);
	}

	public Mono<Boolean> checkRegexPattern(String password, String regex) {

		Pattern pattern = Pattern.compile(regex);
		Matcher matches = pattern.matcher(password);
		return Mono.just(matches.find());
	}
}
