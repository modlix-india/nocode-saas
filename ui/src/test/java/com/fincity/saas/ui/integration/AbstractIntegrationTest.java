package com.fincity.saas.ui.integration;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;

import com.fincity.saas.commons.mongo.repository.InheritanceService;
import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.feign.IFeignCoreService;
import com.fincity.saas.ui.feign.IFeignSecurityBillingService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Base for `ui` integration tests.
 *
 * The `ui` service has never had a Spring context in tests, so a few things had
 * to be arranged before one would start:
 *
 * - `application.yml` does `config.import: configserver:...`, fatal when the
 *   config server is down. `application-test.yml` disables it.
 * - Redis is left unconfigured. `AbstractBaseConfiguration.redisClient()` returns
 *   null on a blank url, every downstream redis bean follows, and `CacheService`
 *   is null-safe on all of them and degrades to its Caffeine L1. So no Redis
 *   container is needed, but note that means the L2 and its pub/sub invalidation
 *   are NOT under test here.
 * - Every outbound Feign client is mocked, matching how `security`'s own base
 *   class neutralizes `IFeignFilesService` and friends.
 *
 * The container is a static field started once per JVM and shared by every
 * subclass, the same arrangement `security` uses. Unlike `security` there is no
 * seed data to protect, so `cleanupCollections()` can drop wholesale.
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
        registry.add("spring.data.mongodb.uri", () -> mongo.getReplicaSetUrl("uitest"));
    }

    @Autowired
    protected ReactiveMongoTemplate mongoTemplate;

    @Autowired
    protected CacheService cacheService;

    /**
     * Stubbed rather than exercised. `InheritanceService.order` wraps the feign
     * call in `cacheService.cacheValueOrGet(CACHE_NAME_INHERITANCE_ORDER, ...)`,
     * so stubbing one layer lower at `IFeignSecurityService` would let a cached
     * chain from an earlier test shadow the stub. Stubbing here sidesteps that
     * entirely.
     */
    @MockitoBean
    protected InheritanceService inheritanceService;

    @MockitoBean
    protected FeignAuthenticationService feignAuthenticationService;

    @MockitoBean
    protected IFeignSecurityService feignSecurityService;

    @MockitoBean
    protected IFeignCoreService feignCoreService;

    @MockitoBean
    protected IFeignSecurityBillingService feignSecurityBillingService;

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

    /**
     * Drop every collection. There is no migration-seeded data in the `ui`
     * Mongo, so unlike the `security` suite this can be unconditional.
     */
    protected void cleanupCollections() {
        this.mongoTemplate.getCollectionNames()
                .filter(name -> !name.startsWith("system."))
                .flatMap(name -> this.mongoTemplate.dropCollection(name))
                .then()
                .block();
    }

    /**
     * Default to "everything is allowed" so a test that is about the override
     * chain does not have to restate the access model. Tests that are about
     * authorization override these.
     */
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

        // Default chain is the single base client. Tests call setInheritance for more.
        this.setInheritance(List.of(SYSTEM));
    }

    /**
     * Set the client inheritance chain the override machinery will see, base
     * first. This is the single value everything in `ui` consumes from the
     * security service's client graph, which is why the chain fixtures can be
     * shared with the `security` suite without sharing a harness.
     */
    protected void setInheritance(List<String> chainBaseFirst) {
        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(chainBaseFirst));
    }

    /**
     * A ContextAuthentication for the given client, carrying the authorities the
     * manual accessCheck in AbstractOverridableDataService looks for.
     */
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

    protected ContextAuthentication systemAuth(String... authorities) {
        return this.authFor(SYSTEM, authorities);
    }

    /**
     * Put the pipeline on the draft surface, the way JWTTokenFilter does when the
     * gateway resolves a DRAFT hostname. Tests use this rather than a header
     * because the context key is what every read path actually consults.
     */
    protected <T> Mono<T> onDraftSurface(Mono<T> mono) {
        return mono.contextWrite(Context.of(LogUtil.DRAFT_KEY, Boolean.TRUE));
    }

    /**
     * The full authority set for one object type, so a test does not have to
     * spell out CREATE/READ/UPDATE/DELETE every time.
     */
    protected static String[] allAuthoritiesFor(String objectName) {
        return new String[] { "Authorities." + objectName + "_CREATE", "Authorities." + objectName + "_READ",
                "Authorities." + objectName + "_UPDATE", "Authorities." + objectName + "_DELETE" };
    }

    protected static SimpleGrantedAuthority authority(String name) {
        return new SimpleGrantedAuthority(name);
    }

    /**
     * Insert a document straight into Mongo, bypassing the service layer.
     * Fixtures for override chains need the stored (delta) form, and going
     * through create() would re-run extractOverride and defeat the point.
     */
    protected <T> T insertRaw(T document) {
        return this.mongoTemplate.insert(document).block();
    }

    protected <T> Flux<T> findAllRaw(Class<T> type) {
        return this.mongoTemplate.findAll(type);
    }
}
