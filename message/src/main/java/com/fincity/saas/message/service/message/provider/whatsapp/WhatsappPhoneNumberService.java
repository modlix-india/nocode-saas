package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.feign.IFeignEntityProcessorService;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumbersRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.message.whatsapp.data.FbPagingData;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.phone.RequestCode;
import com.fincity.saas.message.model.message.whatsapp.phone.VerifyCode;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.WhatsappApiFactory;
import com.fincity.saas.message.service.message.provider.whatsapp.business.WhatsappBusinessManagementApi;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class WhatsappPhoneNumberService
        extends AbstractMessageService<MessageWhatsappPhoneNumbersRecord, WhatsappPhoneNumber, WhatsappPhoneNumberDAO> {

    public static final String WHATSAPP_PHONE_NUMBER_PROVIDER_URI = "/whatsapp/phone";

    private static final String WHATSAPP_PHONE_NUMBER_CACHE = "whatsappPhoneNumber";

    /**
     * Words that say which kind of id follows in a cache key.
     *
     * <p>Every lookup here hangs off the same {@code appCode:clientCode} prefix and is then keyed on
     * a bare number, but the numbers come from unrelated sequences: this table's own row id, a
     * business-account row id, a product id. Without the discriminator {@code getByProductId(access,
     * 3)} and the inherited {@code readById(access, 3)} write the same key and each silently serves
     * the other's answer. All three sequences start at 1, so the overlap is the common case rather
     * than a corner one, and the symptom would be a message sent from someone else's number.
     */
    private static final String PHONE_NUMBER_ID_KEY = "phoneNumberId";

    private static final String ACCOUNT_ID_KEY = "accountId";

    private static final String PRODUCT_ID_KEY = "productId";

    private static final String CODE_METHOD_PARAM = "codeMethod";

    private static final String CODE_PARAM = "code";

    /**
     * Who receives conversations on a newly synced number.
     *
     * <p>A constant rather than configuration because there is exactly one consumer, and a wrong
     * value here is worse than no value: messages would dispatch to a service that does not handle
     * them instead of parking visibly. Reassignment is a per-number decision, not a deployment-wide
     * one.
     */
    private static final String DEFAULT_OWNER_SERVICE = "entity-processor";

    /**
     * The fields a status refresh asks Meta for.
     *
     * <p>Asked for by name, so anything left out comes back null and is written over the value we
     * already hold. {@code codeVerificationStatus} belongs here because verification is the one
     * status a tenant changes from inside this product, and a refresh that skipped it would show a
     * just-verified number as still unverified.
     */
    private static final String[] STATUS_FIELDS = {
        PhoneNumber.Fields.status,
        PhoneNumber.Fields.qualityScore,
        PhoneNumber.Fields.messagingLimitTier,
        PhoneNumber.Fields.nameStatus,
        PhoneNumber.Fields.codeVerificationStatus
    };

    private final WhatsappApiFactory whatsappApiFactory;
    private WhatsappBusinessAccountService businessAccountService;
    private IFeignEntityProcessorService entityProcessorService;

    @Autowired
    public WhatsappPhoneNumberService(WhatsappApiFactory whatsappApiFactory) {
        this.whatsappApiFactory = whatsappApiFactory;
    }

    @Autowired
    public void setBusinessAccountService(WhatsappBusinessAccountService businessAccountService) {
        this.businessAccountService = businessAccountService;
    }

    @Autowired
    public void setEntityProcessorService(IFeignEntityProcessorService entityProcessorService) {
        this.entityProcessorService = entityProcessorService;
    }

    @Override
    protected String getCacheName() {
        return WHATSAPP_PHONE_NUMBER_CACHE;
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.WHATSAPP_PHONE_NUMBER;
    }

    @Override
    protected Mono<Boolean> evictCache(WhatsappPhoneNumber entity) {
        return super.evictCache(entity).flatMap(evicted -> Mono.zip(
                        this.cacheService.evict(
                                this.getCacheName(),
                                this.phoneNumberIdCacheKey(
                                        entity.getAppCode(), entity.getClientCode(), entity.getPhoneNumberId())),
                        this.cacheService.evict(
                                this.getCacheName(),
                                this.accountIdCacheKey(
                                        entity.getAppCode(),
                                        entity.getClientCode(),
                                        entity.getWhatsappBusinessAccountId())),
                        this.cacheService.evict(
                                this.getCacheName(),
                                this.accountAndPhoneNumberIdCacheKey(
                                        entity.getAppCode(),
                                        entity.getClientCode(),
                                        entity.getWhatsappBusinessAccountId(),
                                        entity.getPhoneNumberId())),
                        // The product entry was never evicted here, so a number that changed hands
                        // kept answering for the product it used to serve until the entry aged out.
                        this.evictProductCache(entity.getAppCode(), entity.getClientCode(), entity.getProductId()))
                .map(sEvicted ->
                        sEvicted.getT1() && sEvicted.getT2() && sEvicted.getT3() && sEvicted.getT4()));
    }

    private String phoneNumberIdCacheKey(String appCode, String clientCode, String phoneNumberId) {
        return super.getCacheKey(appCode, clientCode, PHONE_NUMBER_ID_KEY, phoneNumberId);
    }

    private String accountIdCacheKey(String appCode, String clientCode, ULong whatsappBusinessAccountId) {
        return super.getCacheKey(appCode, clientCode, ACCOUNT_ID_KEY, whatsappBusinessAccountId);
    }

    private String accountAndPhoneNumberIdCacheKey(
            String appCode, String clientCode, ULong whatsappBusinessAccountId, String phoneNumberId) {
        return super.getCacheKey(
                appCode, clientCode, ACCOUNT_ID_KEY, whatsappBusinessAccountId, PHONE_NUMBER_ID_KEY, phoneNumberId);
    }

    private String productIdCacheKey(String appCode, String clientCode, ULong productId) {
        return super.getCacheKey(appCode, clientCode, PRODUCT_ID_KEY, productId);
    }

    /**
     * Drops the entry a product lookup would have hit.
     *
     * <p>A null product is not a key: {@code getCacheKey} skips nulls, so building one would
     * produce the bare {@code appCode:clientCode:productId} prefix and evict something that is not
     * this number's entry.
     */
    private Mono<Boolean> evictProductCache(String appCode, String clientCode, ULong productId) {

        if (productId == null) return Mono.just(Boolean.TRUE);

        return this.cacheService.evict(this.getCacheName(), this.productIdCacheKey(appCode, clientCode, productId));
    }

    @Override
    protected Mono<WhatsappPhoneNumber> updatableEntity(WhatsappPhoneNumber entity) {
        return super.updatableEntity(entity).flatMap(uEntity -> {
            uEntity.setProductId(entity.getProductId());
            uEntity.setQualityRating(entity.getQualityRating());
            uEntity.setQualityScore(entity.getQualityScore());
            uEntity.setVerifiedName(entity.getVerifiedName());
            uEntity.setCodeVerificationStatus(entity.getCodeVerificationStatus());
            uEntity.setNameStatus(entity.getNameStatus());
            uEntity.setPlatformType(entity.getPlatformType());
            uEntity.setThroughput(entity.getThroughput());
            uEntity.setStatus(entity.getStatus());
            uEntity.setMessagingLimitTier(entity.getMessagingLimitTier());
            uEntity.setIsDefault(entity.getIsDefault());
            uEntity.setWebhookConfig(entity.getWebhookConfig());
            return Mono.just(uEntity);
        });
    }

    @Override
    public ConnectionSubType getConnectionSubType() {
        return ConnectionSubType.WHATSAPP;
    }

    @Override
    public String getProviderUri() {
        return WHATSAPP_PHONE_NUMBER_PROVIDER_URI;
    }

    public Flux<WhatsappPhoneNumber> syncPhoneNumbers(String connectionName) {
        return FlatMapUtil.flatMapFlux(
                () -> super.hasAccess().flux(),
                access -> this.getPhoneNumbers(connectionName, access).flux(),
                (access, phoneNumbers) -> this.savePhoneNumbers(phoneNumbers.getT1(), phoneNumbers.getT2(), access));
    }

    public Mono<WhatsappPhoneNumber> syncPhoneNumber(String connectionName, Identity whatsappPhoneNumberId) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> super.readIdentityWithAccess(access, whatsappPhoneNumberId),
                (access, whatsappPhoneNumber) ->
                        this.getPhoneNumber(connectionName, access, whatsappPhoneNumber.getPhoneNumberId()),
                (access, whatsappPhoneNumber, phoneNumber) ->
                        this.savePhoneNumber(phoneNumber.getT1(), phoneNumber.getT2(), access));
    }

    /**
     * Makes a number the one its account falls back to, taking the flag off whoever held it.
     *
     * <p>The number it displaces is looked up within the same business account, so a tenant running
     * two WABAs keeps a default on each instead of the two accounts fighting over one flag. Making
     * this tenant-wide would leave the second account with no fallback at all, which is the same
     * hole the send path used to have.
     */
    public Mono<WhatsappPhoneNumber> setDefault(Identity phoneNumber) {
        return FlatMapUtil.flatMapMonoWithNull(
                super::hasAccess,
                access -> super.readIdentityWithAccess(access, phoneNumber),
                (access, whatsappPhoneNumber) ->
                        this.getDefaultPhoneNumber(access, whatsappPhoneNumber.getWhatsappBusinessAccountId()),
                (access, whatsappPhoneNumber, defaultPhoneNumber) -> {
                    if (defaultPhoneNumber == null) return this.updateDefault(whatsappPhoneNumber, Boolean.TRUE);

                    if (whatsappPhoneNumber.getId().equals(defaultPhoneNumber.getId()))
                        return Mono.just(defaultPhoneNumber);

                    return Mono.zip(
                                    this.updateDefault(whatsappPhoneNumber, Boolean.TRUE),
                                    this.updateDefault(defaultPhoneNumber, Boolean.FALSE))
                            .map(Tuple2::getT1);
                });
    }

    /**
     * Points a number at a product, moves it to a different one, or unmaps it entirely.
     *
     * <p>A null {@code productId} means unmap. Refusing to change an existing mapping was never a
     * safety property: numbers get retired and products get split or merged, and the refusal left an
     * UPDATE against the database as the only way to correct a mapping made by mistake.
     *
     * <p>One number per product, so handing a product to a second number takes it off the first
     * rather than leaving two rows claiming it. {@code getByProductId} returns a single row, so a
     * tie there would be broken by whatever order the database felt like, and the losing number
     * would simply stop being used with no sign of why.
     */
    public Mono<WhatsappPhoneNumber> setProductId(Identity phoneNumber, ULong productId) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readIdentityWithAccess(access, phoneNumber)
                                .flatMap(whatsappPhoneNumber ->
                                        this.checkProductAssignment(whatsappPhoneNumber, productId)),
                        (access, whatsappPhoneNumber) -> this.checkProduct(access, productId),
                        (access, whatsappPhoneNumber, checked) ->
                                this.releaseProduct(access, whatsappPhoneNumber, productId),
                        (access, whatsappPhoneNumber, checked, released) ->
                                this.assignProduct(whatsappPhoneNumber, productId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappPhoneNumberService.setProductId"));
    }

    private Mono<WhatsappPhoneNumber> checkProductAssignment(
            WhatsappPhoneNumber whatsappPhoneNumber, ULong productId) {

        // Unmapping only ever widens what the number is allowed to serve, so there is nothing to
        // refuse - including on the default number, whose mapping should be null anyway.
        if (productId == null) return Mono.just(whatsappPhoneNumber);

        if (Boolean.TRUE.equals(whatsappPhoneNumber.getIsDefault()))
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.PRODUCT_TO_DEFAULT);

        return Mono.just(whatsappPhoneNumber);
    }

    /**
     * Fails rather than storing a product id that entity-processor does not recognise, since
     * nothing downstream reads this column back through a foreign key.
     */
    private Mono<Boolean> checkProduct(MessageAccess access, ULong productId) {

        if (productId == null) return Mono.just(Boolean.TRUE);

        return this.entityProcessorService
                .getProductInternal(access.getAppCode(), access.getClientCode(), productId.toBigInteger())
                .map(product -> Boolean.TRUE)
                .switchIfEmpty(super.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        MessageResourceService.IDENTITY_WRONG,
                        "Product",
                        productId));
    }

    /**
     * Takes the product off whichever other number is holding it, so the winner is decided here
     * rather than by row order in a later read.
     */
    private Mono<Boolean> releaseProduct(
            MessageAccess access, WhatsappPhoneNumber whatsappPhoneNumber, ULong productId) {

        if (productId == null) return Mono.just(Boolean.TRUE);

        return this.getByProductId(access, productId)
                .filter(holder -> !holder.getId().equals(whatsappPhoneNumber.getId()))
                .flatMap(holder -> super.updateInternal(holder.setProductId(null)))
                .thenReturn(Boolean.TRUE);
    }

    /**
     * Writes the new mapping and clears the entry the old one was cached under.
     *
     * <p>{@code evictCache} works off the entity it is handed, which by then carries the new product
     * id, so the key for the product this number used to serve would otherwise keep answering with
     * this number until it aged out.
     */
    private Mono<WhatsappPhoneNumber> assignProduct(WhatsappPhoneNumber whatsappPhoneNumber, ULong productId) {

        ULong previousProductId = whatsappPhoneNumber.getProductId();

        return super.updateInternal(whatsappPhoneNumber.setProductId(productId))
                .flatMap(updated -> this.evictProductCache(
                                updated.getAppCode(), updated.getClientCode(), previousProductId)
                        .map(evicted -> updated));
    }

    public Flux<WhatsappPhoneNumber> updatePhoneNumbersStatus(String connectionName) {
        return FlatMapUtil.flatMapFlux(
                () -> super.hasAccess().flux(),
                access -> this.getPhoneNumbers(connectionName, access, STATUS_FIELDS)
                        .flux(),
                (access, phoneNumbers) -> this.updatePhoneNumbersStatus(phoneNumbers.getT2(), access));
    }

    public Mono<WhatsappPhoneNumber> updatePhoneNumberStatus(String connectionName, Identity whatsappPhoneNumberId) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> super.readIdentityWithAccess(access, whatsappPhoneNumberId),
                (access, whatsappPhoneNumber) -> this.getPhoneNumber(
                        connectionName, access, whatsappPhoneNumber.getPhoneNumberId(), STATUS_FIELDS),
                (access, whatsappPhoneNumber, phoneNumber) ->
                        this.updatePhoneNumberStatus(phoneNumber.getT2(), access));
    }

    /**
     * Asks Meta to send the registration code to the number itself, by SMS or voice call.
     *
     * <p>Meta will not let an unverified number send anything, and until now the only way to move
     * one out of that state was the Business Manager UI: the number sat in the table as
     * NOT_VERIFIED with nothing in the product able to act on it. The Graph calls have been wired up
     * since the API client was written, just never reachable.
     */
    public Mono<Response> requestCode(String connectionName, Identity whatsappPhoneNumberId, RequestCode requestCode) {

        if (requestCode == null || requestCode.getCodeMethod() == null)
            return super.throwMissingParam(CODE_METHOD_PARAM);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readIdentityWithAccess(access, whatsappPhoneNumberId),
                        (access, whatsappPhoneNumber) -> super.messageConnectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), connectionName),
                        (access, whatsappPhoneNumber, connection) -> this.getBusinessManagementApi(connection),
                        (access, whatsappPhoneNumber, connection, api) ->
                                api.requestCode(whatsappPhoneNumber.getPhoneNumberId(), requestCode))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappPhoneNumberService.requestCode"));
    }

    /**
     * Hands Meta the code that arrived on the number and returns the row as Meta now sees it.
     *
     * <p>The refresh is the point of returning a phone number rather than the bare provider
     * response. Verification changes what Meta will let the number do, so a caller that got only
     * "success" would be showing a stale row until someone ran a sync, and the obvious next question
     * from the screen is whether the number is usable now.
     */
    public Mono<WhatsappPhoneNumber> verifyCode(
            String connectionName, Identity whatsappPhoneNumberId, VerifyCode verifyCode) {

        if (verifyCode == null || verifyCode.getCode() == null || verifyCode.getCode().isBlank())
            return super.throwMissingParam(CODE_PARAM);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readIdentityWithAccess(access, whatsappPhoneNumberId),
                        (access, whatsappPhoneNumber) -> super.messageConnectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), connectionName),
                        (access, whatsappPhoneNumber, connection) -> this.getBusinessManagementApi(connection),
                        (access, whatsappPhoneNumber, connection, api) ->
                                api.verifyCode(whatsappPhoneNumber.getPhoneNumberId(), verifyCode),
                        (access, whatsappPhoneNumber, connection, api, response) ->
                                this.refreshAfterVerification(connectionName, whatsappPhoneNumberId, response))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappPhoneNumberService.verifyCode"));
    }

    /**
     * A wrong code comes back as a Graph error and never reaches here, so the false case is Meta
     * declining without saying why. Reporting that as a failed verification is closer to the truth
     * than handing back a row that still says NOT_VERIFIED and letting the screen guess.
     */
    private Mono<WhatsappPhoneNumber> refreshAfterVerification(
            String connectionName, Identity whatsappPhoneNumberId, Response response) {

        if (!response.isSuccess())
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.PHONE_NUMBER_VERIFICATION_FAILED);

        return this.updatePhoneNumberStatus(connectionName, whatsappPhoneNumberId);
    }

    private Mono<WhatsappPhoneNumber> updateDefault(WhatsappPhoneNumber whatsappPhoneNumber, Boolean isDefault) {
        return super.update(whatsappPhoneNumber.setIsDefault(isDefault))
                .flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    /**
     * The tenant's business numbers, for internal callers only.
     *
     * <p>Takes the tenant explicitly rather than reading it off a security context, because the
     * caller is another service and there is no user on this hop. Built the same way as {@code
     * WhatsappMessageService.readByTicketInternal}: an access from the two codes, then the ordinary
     * access condition, so an internal caller still sees exactly one tenant's rows.
     */
    public Mono<Page<WhatsappPhoneNumber>> readPageInternal(String appCode, String clientCode, Pageable pageable) {

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);

        return this.dao
                .messageAccessCondition(null, access)
                .flatMap(condition -> this.dao.readPageFilter(pageable, condition))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappPhoneNumberService.readPageInternal"));
    }

    /**
     * The number a send falls back to when the product names none, for internal callers only.
     *
     * <p>Empty when the tenant has never marked one, which is an ordinary state rather than an
     * error: a tenant with a single number, or one number per product, needs no default. Callers
     * decide what to show for it.
     *
     * <p>Filters here rather than calling {@code WhatsappPhoneNumberDAO.getDefaultPhoneNumber},
     * because that one is scoped to a business account and this caller has none to give: it is
     * asking the tenant-wide question the deal profile asks, which is "which number is preselected
     * in the composer". A tenant running two business accounts has a default on each, so the oldest
     * row wins and the agent can still change it. Ordering by id keeps repeat calls stable, which
     * an unordered read would not.
     */
    public Mono<WhatsappPhoneNumber> getDefaultInternal(String appCode, String clientCode) {

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);

        return this.dao
                .messageAccessCondition(
                        FilterCondition.make(WhatsappPhoneNumber.Fields.isDefault, Boolean.TRUE)
                                .setOperator(FilterConditionOperator.IS_TRUE),
                        access)
                .flatMap(condition ->
                        this.dao.readPageFilter(PageRequest.of(0, 1, Sort.Direction.ASC, "id"), condition))
                .flatMap(page -> page.isEmpty() ? Mono.empty() : Mono.just(page.getContent().getFirst()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappPhoneNumberService.getDefaultInternal"));
    }

    public Mono<WhatsappPhoneNumber> getByPhoneNumberId(MessageAccess access, String phoneNumberId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getByPhoneNumberId(access, phoneNumberId),
                this.phoneNumberIdCacheKey(access.getAppCode(), access.getClientCode(), phoneNumberId));
    }

    /**
     * Which tenant owns a Meta phone number id, for the inbound webhook to route on.
     *
     * <p>Deliberately uncached. The access-scoped read above caches under a key built from the
     * tenant, and there is no tenant here yet - that is the question being asked. Caching this by
     * phone number id alone would need its own key namespace and its own eviction on every sync and
     * reassignment, to save one indexed lookup on a unique key. Not worth the way that goes wrong.
     */
    public Mono<WhatsappPhoneNumber> getByPhoneNumberIdInternal(String phoneNumberId) {
        return this.dao.getByPhoneNumberIdInternal(phoneNumberId);
    }

    /**
     * The number an account sends from when nothing more specific is chosen.
     *
     * <p>Scoped to the account, not the tenant. A number is only a sensible default among the other
     * numbers on the same WABA, because that is the business identity the customer sees and the
     * account the reply comes back to.
     */
    public Mono<WhatsappPhoneNumber> getDefaultPhoneNumber(MessageAccess access, ULong whatsappBusinessAccountId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getDefaultPhoneNumber(access, whatsappBusinessAccountId),
                this.accountIdCacheKey(access.getAppCode(), access.getClientCode(), whatsappBusinessAccountId));
    }

    public Mono<WhatsappPhoneNumber> getByAccountAndPhoneNumberId(
            MessageAccess access, ULong whatsappBusinessAccountId, String phoneNumberId) {
        return this.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> this.dao.getByAccountAndPhoneNumberId(access, whatsappBusinessAccountId, phoneNumberId),
                        this.accountAndPhoneNumberIdCacheKey(
                                access.getAppCode(), access.getClientCode(), whatsappBusinessAccountId, phoneNumberId))
                .switchIfEmpty(this.getDefaultPhoneNumber(access, whatsappBusinessAccountId));
    }

    /**
     * The number mapped to a product, if any.
     *
     * <p>Empty is a normal answer and is deliberately not cached: {@code cacheValueOrGet} only
     * stores what the supplier emits, so a product looked up before anyone mapped it does not leave
     * behind an entry that would keep answering "none" after the mapping is made.
     */
    public Mono<WhatsappPhoneNumber> getByProductId(MessageAccess access, ULong productId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getByProductId(access, productId),
                this.productIdCacheKey(access.getAppCode(), access.getClientCode(), productId));
    }

    private Mono<Tuple2<WhatsappBusinessAccount, FbPagingData<PhoneNumber>>> getPhoneNumbers(
            String connectionName, MessageAccess access, String... fields) {
        return FlatMapUtil.flatMapMono(
                () -> super.messageConnectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), connectionName),
                connection -> getWhatsappBusinessAccount(access, connection),
                (connection, businessAccount) -> this.getBusinessManagementApi(connection),
                (connection, businessAccount, api) ->
                        api.retrievePhoneNumbers(businessAccount.getWhatsappBusinessAccountId(), fields),
                (connection, businessAccount, api, phoneNumbers) ->
                        Mono.just(Tuples.of(businessAccount, phoneNumbers)));
    }

    private Mono<Tuple2<WhatsappBusinessAccount, PhoneNumber>> getPhoneNumber(
            String connectionName, MessageAccess access, String phoneNumberId, String... fields) {
        return FlatMapUtil.flatMapMono(
                () -> super.messageConnectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), connectionName),
                connection -> getWhatsappBusinessAccount(access, connection),
                (connection, businessAccount) -> this.getBusinessManagementApi(connection),
                (connection, businessAccount, api) -> api.retrievePhoneNumber(phoneNumberId, fields),
                (connection, businessAccount, api, phoneNumber) -> Mono.just(Tuples.of(businessAccount, phoneNumber)));
    }

    private Flux<WhatsappPhoneNumber> savePhoneNumbers(
            WhatsappBusinessAccount whatsappBusinessAccount,
            FbPagingData<PhoneNumber> phoneNumbers,
            MessageAccess access) {
        return Flux.fromIterable(phoneNumbers.getData())
                .flatMap(phoneNumber -> this.savePhoneNumber(whatsappBusinessAccount, phoneNumber, access));
    }

    /**
     * Records one number from a sync, and gives a new one an owner.
     *
     * <p>{@code OWNER_SERVICE} is what the inbound path routes on, and nothing else in the product
     * sets it: there is no UI for it and no endpoint. A number synced without one parks every
     * message it receives in the outbox with a configuration error, recoverable but silent until
     * somebody reads the log. Defaulting it at the one point where numbers are created closes that,
     * and {@code entity-processor} is the only consumer there is - the same default
     * {@code ExotelCallController} already applies to a call.
     *
     * <p>Only set on create. A re-sync must not overwrite a deliberate reassignment, and sync runs
     * often.
     */
    private Mono<WhatsappPhoneNumber> savePhoneNumber(
            WhatsappBusinessAccount whatsappBusinessAccount, PhoneNumber phoneNumber, MessageAccess access) {

        return FlatMapUtil.flatMapMono(
                        () -> this.dao.getByPhoneNumberId(access, phoneNumber.getId()),
                        whatsappPhoneNumber -> super.update(whatsappPhoneNumber.update(phoneNumber)),
                        (whatsappPhoneNumber, uWhatsappPhoneNumber) ->
                                this.evictCache(uWhatsappPhoneNumber).map(evicted -> whatsappPhoneNumber))
                .switchIfEmpty(Mono.defer(() -> super.createInternal(
                        access,
                        WhatsappPhoneNumber.of(whatsappBusinessAccount.getId(), phoneNumber)
                                .setOwnerService(DEFAULT_OWNER_SERVICE))));
    }

    private Flux<WhatsappPhoneNumber> updatePhoneNumbersStatus(
            FbPagingData<PhoneNumber> phoneNumbers, MessageAccess access) {
        return Flux.fromIterable(phoneNumbers.getData())
                .flatMap(phoneNumber -> this.updatePhoneNumberStatus(phoneNumber, access));
    }

    private Mono<WhatsappPhoneNumber> updatePhoneNumberStatus(PhoneNumber phoneNumber, MessageAccess access) {

        return FlatMapUtil.flatMapMono(
                () -> this.dao.getByPhoneNumberId(access, phoneNumber.getId()),
                whatsappPhoneNumber -> super.update(whatsappPhoneNumber.updateStatus(phoneNumber)),
                (whatsappPhoneNumber, uWhatsappPhoneNumber) ->
                        this.evictCache(uWhatsappPhoneNumber).map(evicted -> whatsappPhoneNumber));
    }

    private Mono<WhatsappBusinessAccount> getWhatsappBusinessAccount(MessageAccess access, Connection connection) {
        String businessAccountId = (String) connection
                .getConnectionDetails()
                .getOrDefault(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId, null);

        if (businessAccountId == null)
            return super.throwMissingParam(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId);

        return this.businessAccountService.getBusinessAccount(access, businessAccountId);
    }

    private Mono<WhatsappBusinessManagementApi> getBusinessManagementApi(Connection connection) {
        return this.whatsappApiFactory.newBusinessManagementApiFromConnection(connection);
    }

}
