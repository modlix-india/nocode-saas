package com.fincity.saas.core.integration;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;

import com.fincity.saas.commons.core.feign.IFeignEntityProcessor;
import com.fincity.saas.commons.core.feign.IFeignFilesService;
import com.fincity.saas.commons.mongo.repository.InheritanceService;
import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.feign.IFeignSecurityBillingService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Base for `core` integration tests. Mirrors the `ui` equivalent; see that class
 * for the reasoning behind the shadowed application.yml and the absent Redis.
 *
 * `core` additionally carries R2DBC, Flyway and RabbitMQ for its relational and
 * messaging sides. None of that is touched by the overridable-document stack, so
 * application-test.yml excludes those autoconfigurations and the harness stays at
 * one container. A test that needs the relational side must add a MySQL container
 * and re-enable them rather than assume they are up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String SYSTEM = "SYSTEM";
    protected static final String APP_CODE = "testapp";

    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0").withReuse(true);

    static {
        mongo.start();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongo.getReplicaSetUrl("coretest"));
    }

    @Autowired
    protected ReactiveMongoTemplate mongoTemplate;

    @Autowired
    protected CacheService cacheService;

    /**
     * Stubbed rather than exercised. `InheritanceService.order` wraps its feign call
     * in `cacheService.cacheValueOrGet(CACHE_NAME_INHERITANCE_ORDER, ...)`, so
     * stubbing one layer lower would let a cached chain from an earlier test shadow
     * the stub.
     */
    @MockitoBean
    protected InheritanceService inheritanceService;

    @MockitoBean
    protected FeignAuthenticationService feignAuthenticationService;

    @MockitoBean
    protected IFeignSecurityService feignSecurityService;

    @MockitoBean
    protected IFeignSecurityBillingService feignSecurityBillingService;

    @MockitoBean
    protected IFeignFilesService feignFilesService;

    @MockitoBean
    protected IFeignEntityProcessor feignEntityProcessor;

    /**
     * RabbitAutoConfiguration is excluded, but beans still take these by
     * constructor, so the types have to resolve. `security`'s base class mocks
     * CachingConnectionFactory for the same reason.
     */
    @MockitoBean
    protected AmqpTemplate amqpTemplate;

    @MockitoBean
    protected CachingConnectionFactory cachingConnectionFactory;

    @BeforeEach
    void baseSetup() {
        this.cleanupCollections();
        this.cacheService.evictAllCaches().block();
        this.stubPermissiveSecurity();
    }

    @AfterEach
    void baseTeardown() {
        this.cleanupCollections();
        this.cacheService.evictAllCaches().block();
    }

    protected void cleanupCollections() {
        this.mongoTemplate.getCollectionNames()
                .filter(name -> !name.startsWith("system."))
                .flatMap(name -> this.mongoTemplate.dropCollection(name))
                .then()
                .block();
    }

    protected void stubPermissiveSecurity() {

        Mockito.when(this.feignAuthenticationService.hasReadAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
        Mockito.when(this.feignAuthenticationService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.TRUE));
        Mockito.when(this.feignAuthenticationService.getAppExplicitInfoByCode(Mockito.anyString()))
                .thenReturn(Mono.just(new App().setAppCode(APP_CODE)
                        .setClientCode(SYSTEM)
                        .setAppAccessType("ANY")));

        // AppDataService resolves the effective client through IFeignSecurityService
        // directly, not through FeignAuthenticationService, so both have to be
        // stubbed or a data call NPEs on a null Mono.
        Mockito.when(this.feignSecurityService.doesClientManageClientCode(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
        Mockito.when(this.feignSecurityService.hasReadAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
        Mockito.when(this.feignSecurityService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));

        // The storage read path resolves an app's dependency list before it will
        // serve anything. No dependencies is the right default for a test app.
        Mockito.when(this.feignAuthenticationService.getDependencies(Mockito.anyString()))
                .thenReturn(Mono.just(List.of()));

        this.setInheritance(List.of(SYSTEM));
    }

    /**
     * Set the client inheritance chain the override machinery will see, base first.
     * This is the single value the overridable stack consumes from security's client
     * graph, which is why chain fixtures can be shared with the `security` suite
     * without sharing a harness.
     */
    protected void setInheritance(List<String> chainBaseFirst) {
        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(chainBaseFirst));
    }

    protected ContextAuthentication authFor(String clientCode, String... authorities) {

        ContextUser user = new ContextUser();
        user.setId(BigInteger.ONE);
        user.setClientId(BigInteger.ONE);
        user.setUserName("test-" + clientCode);
        user.setStringAuthorities(List.of(authorities));

        // setAuthenticated returns void: Spring's Authentication interface declares it
        // that way, which overrides Lombok's chained accessor. So it cannot join the chain.
        ContextAuthentication ca = new ContextAuthentication()
                .setUser(user)
                .setLoggedInFromClientId(BigInteger.ONE)
                .setLoggedInFromClientCode(clientCode)
                .setClientTypeCode(SYSTEM.equals(clientCode) ? ContextAuthentication.CLIENT_TYPE_SYSTEM : "BUS")
                .setClientCode(clientCode)
                .setAccessToken("test-token")
                .setUrlClientCode(clientCode)
                .setUrlAppCode(APP_CODE);
        ca.setAuthenticated(true);
        return ca;
    }

    /**
     * Put the pipeline on the draft surface, the way JWTTokenFilter does when the
     * gateway resolves a DRAFT hostname.
     */
    protected <T> Mono<T> onDraftSurface(Mono<T> mono) {
        return mono.contextWrite(Context.of(LogUtil.DRAFT_KEY, Boolean.TRUE));
    }

    protected static String[] allAuthoritiesFor(String objectName) {
        return new String[] { "Authorities." + objectName + "_CREATE", "Authorities." + objectName + "_READ",
                "Authorities." + objectName + "_UPDATE", "Authorities." + objectName + "_DELETE" };
    }

    /**
     * Insert straight into Mongo, bypassing the service layer. Override-chain
     * fixtures need the stored (delta) form, and going through create() would
     * re-run extractOverride against the very chain under test.
     */
    protected <T> T insertRaw(T document) {
        return this.mongoTemplate.insert(document).block();
    }

    protected <T> Flux<T> findAllRaw(Class<T> type) {
        return this.mongoTemplate.findAll(type);
    }
}
