package com.fincity.security.service.policy;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.policy.ClientPasswordPolicyDAO;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ClientPasswordPolicyService extends AbstractPolicyService<SecurityClientPasswordPolicyRecord, ClientPasswordPolicy, ClientPasswordPolicyDAO>
		implements IPolicyService<ClientPasswordPolicy>{

	private static final String CLIENT_PASSWORD_POLICY = "client password policy";

	private static final String CACHE_NAME_CLIENT_PWD_POLICY = "clientPasswordPolicy";

	private final Set<Character> specialCharacters = Set.of('~', '`', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
			'_', '-', '+', '=', '{', '}', '[', ']', '|', '\\', '/', ':', ';', '\"', '\'', '<', '>', ',', '.', '?');

	@Override
	public String getPolicyName() {
		return CLIENT_PASSWORD_POLICY;
	}

	@Override
	public AuthenticationPasswordType getAuthenticationPasswordType() {
		return AuthenticationPasswordType.PASSWORD;
	}

	@Override
	public String getPolicyCacheName() {
		return CACHE_NAME_CLIENT_PWD_POLICY;
	}

	@Override
	protected Mono<ClientPasswordPolicy> updatableEntity(ClientPasswordPolicy entity) {
		return this.read(entity.getId())
				.map(e -> {
					e.setAtleastOneDigit(entity.isAtleastOneDigit());
					e.setAtleastOneLowercase(entity.isAtleastOneLowercase());
					e.setAtleastOneSpecialChar(entity.isAtleastOneSpecialChar());
					e.setAtleastOneUppercase(entity.isAtleastOneUppercase());
					e.setNoFailedAttempts(entity.getNoFailedAttempts());
					e.setPassExpiryInDays(entity.getPassExpiryInDays());
					e.setPassExpiryWarnInDays(entity.getPassExpiryWarnInDays());
					e.setPassHistoryCount(entity.getPassHistoryCount());
					e.setPassMaxLength(entity.getPassMaxLength());
					e.setPassMinLength(entity.getPassMinLength());
					e.setPercentageName(entity.getPercentageName());
					e.setRegex(entity.getRegex());
					e.setSpacesAllowed(entity.isSpacesAllowed());
					return e;
				});
	}

	@Override
	public Mono<Boolean> checkAllConditions(ULong clientId, ULong appId, String password) {

		return flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.dao.getByClientIdAndAppId(clientId, appId, ULong.valueOf(ca.getLoggedInFromClientId())),

				(ca, passwordPolicy) -> checkAlphanumericExists(passwordPolicy, password),

				(ca, passwordPolicy, isAlphaNumeric) -> checkInSpecialCharacters(password),

				(ca, passwordPolicy, isAlphaNumeric, isSpecial) -> {

					if (passwordPolicy.isSpacesAllowed())
						return Mono.just(true);

					if (password.indexOf(' ') != -1)
				        return securityMessageResourceService.throwMessage(
				                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
				                SecurityMessageResourceService.SPACES_MISSING);

					return Mono.just(true);
				},

				(ca, passwordPolicy, isAlphaNumeric, isSpecial, isSpace) -> {

					String regex = passwordPolicy.getRegex();

					if (StringUtil.safeIsBlank(regex))
						return Mono.just(true);

					return checkRegexPattern(password, regex);

				},

				(ca, passwordPolicy, isAlphaNumeric, isSpecial, isSpace, isRegex) -> this
						.checkStrengthOfPassword(passwordPolicy, password))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientPasswordPolicyService.checkAllConditions"))
				.defaultIfEmpty(true);
	}

	private Mono<Boolean> checkAlphanumericExists(ClientPasswordPolicy passwordPolicy, String password) {

		if (passwordPolicy.isAtleastOneUppercase() && !checkExistsInBetween(password, 'A', 'Z')) {
			return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
			        SecurityMessageResourceService.CAPTIAL_LETTERS_MISSING);
		}

		if (passwordPolicy.isAtleastOneUppercase() && !checkExistsInBetween(password, 'a', 'z')) {
			return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
			        SecurityMessageResourceService.SMALL_LETTERS_MISSING);
		}

		if (passwordPolicy.isAtleastOneDigit() && !checkExistsInBetween(password, '0', '9')) {
			return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.NUMBERS_MISSING);
		}

		return Mono.just(true);
	}

	private boolean checkExistsInBetween(String password, char minBoundary, char maxBoundary) {

		for (int i = 0; i < password.length(); i++) {

			char ch = password.charAt(i);

			if (ch >= minBoundary && ch <= maxBoundary)
				return true;

		}
		return false;
	}

	private Mono<Boolean> checkStrengthOfPassword(ClientPasswordPolicy passwordPolicy, String password) {

		if (passwordPolicy.getPassMaxLength() != null && password.length() > passwordPolicy.getPassMaxLength()
		        .intValue())
			return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
			        SecurityMessageResourceService.MAX_LENGTH_ERROR, passwordPolicy.getPassMaxLength());

		if (passwordPolicy.getPassMinLength() != null && password.length() < passwordPolicy.getPassMinLength()
		        .intValue())
			return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
			        SecurityMessageResourceService.MIN_LENGTH_ERROR, passwordPolicy.getPassMinLength());

		return Mono.just(true);

	}

	private Mono<Boolean> checkInSpecialCharacters(String password) {

		for (int i = 0; i < password.length(); i++) {
			Character ch = password.charAt(i);
			if (specialCharacters.contains(ch))
				return Mono.just(true);
		}

		return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
		        SecurityMessageResourceService.SPECIAL_CHARACTERS_MISSING);
	}

	private Mono<Boolean> checkRegexPattern(String password, String regex) {

		Pattern pattern = Pattern.compile(regex);
		Matcher matches = pattern.matcher(password);
		if (!matches.find())
			return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
			        SecurityMessageResourceService.REGEX_MISMATCH);

		return Mono.just(true);
	}
}
