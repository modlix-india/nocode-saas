package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.security.dao.ClientPasswordPolicyDAO;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ClientPasswordPolicyService extends
        AbstractJOOQUpdatableDataService<SecurityClientPasswordPolicyRecord, ULong, ClientPasswordPolicy, ClientPasswordPolicyDAO> {

	private static final String CLIENT_PASSWORD_POLICY = "client password policy";

	private static final String CACHE_NAME_CLIENT_PWD_POLICY = "clientPasswordPolicy";

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private CacheService cacheService;

	private final Set<Character> specialCharacters = Set.of('~', '`', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
	        '_', '-', '+', '=', '{', '}', '[', ']', '|', '\\', '/', ':', ';', '\"', '\'', '<', '>', ',', '.', '?');

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_CREATE')")
	@Override
	public Mono<ClientPasswordPolicy> create(ClientPasswordPolicy entity) {

		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> entity.getAppId() == null ? this.dao.checkValidEntity(entity.getClientId())
		                .switchIfEmpty(securityMessageResourceService.throwMessage(
		                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
		                        SecurityMessageResourceService.MANDATORY_APP_ID, entity.getClientId()))
		                : Mono.just(true),

		        (ca, validEntry) ->
				{

			        ULong currentUser = ULong.valueOf(ca.getLoggedInFromClientId());

			        if (ca.isSystemClient() || currentUser.equals(entity.getClientId()))
				        return super.create(entity);

			        return this.clientService.isBeingManagedBy(currentUser, entity.getClientId())
			                .flatMap(managed -> managed.booleanValue() ? super.create(entity) : Mono.empty());
		        }

		).flatMap(e -> cacheService.evict(CACHE_NAME_CLIENT_PWD_POLICY, e.getClientId(), getAppIdIfExists(e.getAppId()))
		        .map(x -> e))
		        .switchIfEmpty(securityMessageResourceService.throwMessage(
		                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
		                SecurityMessageResourceService.FORBIDDEN_CREATE, CLIENT_PASSWORD_POLICY));

	}

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_READ')")
	@Override
	public Mono<ClientPasswordPolicy> read(ULong id) {

		return super.read(id);
	}

	@Override
	protected Mono<ClientPasswordPolicy> updatableEntity(ClientPasswordPolicy entity) {
		return this.read(entity.getId())
		        .map(e ->
				{
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
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		if (fields == null || key == null)
			return Mono.just(new HashMap<String, Object>());

		fields.remove("clientId");
		fields.remove("appId");

		return Mono.just(fields);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_UPDATE')")
	@Override
	public Mono<ClientPasswordPolicy> update(ULong key, Map<String, Object> fields) {

		return this.dao.canBeUpdated(key)
		        .flatMap(e -> e.booleanValue() ? super.update(key, fields) : Mono.empty())
		        .flatMap(e -> cacheService
		                .evict(CACHE_NAME_CLIENT_PWD_POLICY, e.getClientId(), getAppIdIfExists(e.getAppId()))
		                .map(x -> e))
		        .switchIfEmpty(securityMessageResourceService.throwMessage(
		                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
		                SecurityMessageResourceService.FORBIDDEN_CREATE, CLIENT_PASSWORD_POLICY));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_UPDATE')")
	@Override
	public Mono<ClientPasswordPolicy> update(ClientPasswordPolicy entity) {

		return this.dao.canBeUpdated(entity.getId())
		        .flatMap(e -> e.booleanValue() ? super.update(entity) : Mono.empty())
		        .flatMap(e -> cacheService
		                .evict(CACHE_NAME_CLIENT_PWD_POLICY, e.getClientId(), getAppIdIfExists(e.getAppId()))
		                .map(x -> e))
		        .switchIfEmpty(securityMessageResourceService.throwMessage(
		                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
		                SecurityMessageResourceService.FORBIDDEN_CREATE, CLIENT_PASSWORD_POLICY));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {

		return this.dao.canBeUpdated(id)
		        .flatMap(e -> e.booleanValue() ? super.delete(id) : Mono.empty())
		        .flatMap(cacheService.evictFunction(CACHE_NAME_CLIENT_PWD_POLICY, id))
		        .switchIfEmpty(securityMessageResourceService.throwMessage(
		                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
		                SecurityMessageResourceService.FORBIDDEN_CREATE, CLIENT_PASSWORD_POLICY));
	}
	

	public Mono<Boolean> checkAllConditions(ULong clientId, String password) {

		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.getByAppCodeAndClient(ca.getUrlAppCode(), clientId,
		                ULong.valueOf(ca.getLoggedInFromClientId())),

		        (ca, passwordPolicy) -> checkAlphanumericExists(passwordPolicy, password),

		        (ca, passwordPolicy, isAlphaNumberic) -> checkInSpecialCharacters(password),

		        (ca, passwordPolicy, isAlphaNumberic, isSpecial) ->
				{

			        if (passwordPolicy.isSpacesAllowed())
				        return Mono.just(true);

			        if (password.indexOf(' ') != -1)
				        return securityMessageResourceService.throwMessage(
				                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
				                SecurityMessageResourceService.SPACES_MISSING);

			        return Mono.just(true);
		        },

		        (ca, passwordPolicy, isAlphaNumberic, isSpecial, isSpace) ->
				{

			        String regex = passwordPolicy.getRegex();

			        if (StringUtil.safeIsBlank(regex))
				        return Mono.just(true);

			        return checkRegexPattern(password, regex);

		        },

		        (ca, passwordPolicy, isAlphaNumberic, isSpecial, isSpace, isRegex) -> this
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

	private String getAppIdIfExists(ULong id) {
		return !StringUtil.safeIsBlank(id) ? ":" + id : "";
	}

}
