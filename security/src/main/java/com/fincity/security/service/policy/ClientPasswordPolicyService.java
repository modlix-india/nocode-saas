package com.fincity.security.service.policy;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.policy.ClientPasswordPolicyDAO;
import com.fincity.security.dto.PastPassword;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ClientPasswordPolicyService
        extends AbstractPolicyService<SecurityClientPasswordPolicyRecord, ClientPasswordPolicy, ClientPasswordPolicyDAO>
        implements IPolicyService<ClientPasswordPolicy> {

    private static final String CLIENT_PASSWORD_POLICY = "client password policy";

    private static final String CACHE_NAME_CLIENT_PWD_POLICY = "clientPasswordPolicy";

    private final Set<Character> specialCharacters = Set.of('~', '`', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
            '_', '-', '+', '=', '{', '}', '[', ']', '|', '\\', '/', ':', ';', '\"', '\'', '<', '>', ',', '.', '?');

    private final PasswordEncoder encoder;

    protected ClientPasswordPolicyService(SecurityMessageResourceService securityMessageResourceService,
                                          CacheService cacheService, PasswordEncoder encoder) {
        super(securityMessageResourceService, cacheService);
        this.encoder = encoder;
    }

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
    protected Mono<ClientPasswordPolicy> getDefaultPolicy() {
        return Mono.just(
                (ClientPasswordPolicy) new ClientPasswordPolicy()
                        .setAtleastOneUppercase(true)
                        .setAtleastOneLowercase(true)
                        .setAtleastOneDigit(true)
                        .setAtleastOneSpecialChar(true)
                        .setSpacesAllowed(false)
                        .setRegex(null)
                        .setPassExpiryInDays((short) 10)
                        .setPassExpiryWarnInDays((short) 8)
                        .setPassMinLength((short) 12)
                        .setPassMaxLength((short) 20)
                        .setPassHistoryCount((short) 5)
                        .setClientId(ULong.valueOf(0))
                        .setAppId(ULong.valueOf(0))
                        .setNoFailedAttempts((short) 3)
                        .setUserLockTime(15L)
                        .setId(DEFAULT_POLICY_ID));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_READ')")
    @Override
    protected Mono<ClientPasswordPolicy> updatableEntity(ClientPasswordPolicy entity) {
        return this.read(entity.getId())
                .map(e -> {
                    e.setNoFailedAttempts(entity.getNoFailedAttempts());
                    e.setAtleastOneUppercase(entity.isAtleastOneUppercase());
                    e.setAtleastOneLowercase(entity.isAtleastOneLowercase());
                    e.setAtleastOneDigit(entity.isAtleastOneDigit());
                    e.setAtleastOneSpecialChar(entity.isAtleastOneSpecialChar());
                    e.setSpacesAllowed(entity.isSpacesAllowed());
                    e.setRegex(entity.getRegex());
                    e.setPercentageName(entity.getPercentageName());
                    e.setPassExpiryInDays(entity.getPassExpiryInDays());
                    e.setPassExpiryWarnInDays(entity.getPassExpiryWarnInDays());
                    e.setPassMinLength(entity.getPassMinLength());
                    e.setPassMaxLength(entity.getPassMaxLength());
                    e.setPassHistoryCount(entity.getPassHistoryCount());
                    e.setNoFailedAttempts(
                            entity.getNoFailedAttempts() != null ? entity.getNoFailedAttempts() : (short) 3);
                    e.setUserLockTime(entity.getUserLockTime() != null ? entity.getUserLockTime() : 15L);

                    return e;
                });
    }

    @Override
    public Mono<Boolean> checkAllConditions(ULong clientId, ULong appId, ULong userId, String password) {

        return FlatMapUtil.flatMapMono(

                        () -> this.getClientAppPolicy(clientId, appId),

                        passwordPolicy -> this.checkAllConditions(passwordPolicy, userId, password))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientPasswordPolicyService.checkAllConditions"))
                .defaultIfEmpty(true);
    }

    @Override
    public Mono<Boolean> checkAllConditions(ClientPasswordPolicy policy, ULong userId, String password) {
        return FlatMapUtil.flatMapMono(

                        () -> this.checkPastPasswords(policy, userId, password),

                        pastPassCheck -> this.checkAlphanumericExists(policy, password),

                        (pastPassCheck, isAlphaNumeric) -> this.checkInSpecialCharacters(password),

                        (pastPassCheck, isAlphaNumeric, isSpecial) -> {

                            if (policy.isSpacesAllowed())
                                return Mono.just(Boolean.TRUE);

                            if (password.indexOf(' ') != -1)
                                return securityMessageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        SecurityMessageResourceService.SPACES_MISSING);

                            return Mono.just(Boolean.TRUE);
                        },

                        (pastPassCheck, isAlphaNumeric, isSpecial, isSpace) -> {

                            String regex = policy.getRegex();

                            if (StringUtil.safeIsBlank(regex))
                                return Mono.just(Boolean.TRUE);

                            return checkRegexPattern(password, regex);

                        },

                        (pastPassCheck, isAlphaNumeric, isSpecial, isSpace, isRegex) -> this.checkStrengthOfPassword(policy,
                                password))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientPasswordPolicyService.checkAllConditions"))
                .defaultIfEmpty(Boolean.TRUE);
    }

    private Mono<Boolean> checkPastPasswords(ClientPasswordPolicy passwordPolicy, ULong userId, String password) {

        if (userId == null)
            return Mono.just(Boolean.TRUE);

        return this.dao.getPastPasswordsBasedOnPolicy(passwordPolicy, userId)
                .filter(pastPasswords -> isPasswordMatch(pastPasswords, userId, password))
                .next()
                .flatMap(matchedPin -> policyBadRequestException(
                        SecurityMessageResourceService.PASSWORD_USER_ERROR,
                        getAuthenticationPasswordType().getName(),
                        getAuthenticationPasswordType().getName()))
                .switchIfEmpty(Mono.just(Boolean.TRUE));
    }

    private boolean isPasswordMatch(PastPassword pastPassword, ULong userId, String password) {
        return pastPassword.isPasswordHashed() ? encoder.matches(userId + password, pastPassword.getPassword())
                : pastPassword.getPassword().equals(password);
    }

    private Mono<Boolean> checkAlphanumericExists(ClientPasswordPolicy passwordPolicy, String password) {

        if (passwordPolicy.isAtleastOneUppercase() && checkDoesntExistsInBetween(password, 'A', 'Z'))
            return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.CAPITAL_LETTERS_MISSING);

        if (passwordPolicy.isAtleastOneUppercase() && checkDoesntExistsInBetween(password, 'a', 'z'))
            return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.SMALL_LETTERS_MISSING);

        if (passwordPolicy.isAtleastOneDigit() && checkDoesntExistsInBetween(password, '0', '9'))
            return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.NUMBERS_MISSING);

        return Mono.just(Boolean.TRUE);
    }

    private boolean checkDoesntExistsInBetween(String password, char minBoundary, char maxBoundary) {

        for (int i = 0; i < password.length(); i++) {

            char ch = password.charAt(i);
            if (ch < minBoundary || ch > maxBoundary)
                return false;
        }
        return true;
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

        return Mono.just(Boolean.TRUE);

    }

    private Mono<Boolean> checkInSpecialCharacters(String password) {

        for (int i = 0; i < password.length(); i++) {
            Character ch = password.charAt(i);
            if (specialCharacters.contains(ch))
                return Mono.just(Boolean.TRUE);
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

        return Mono.just(Boolean.TRUE);
    }
}
