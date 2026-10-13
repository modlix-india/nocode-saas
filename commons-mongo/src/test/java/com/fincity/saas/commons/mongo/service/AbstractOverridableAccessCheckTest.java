package com.fincity.saas.commons.mongo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.mongo.document.AbstractSchema;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.mongo.repository.InheritanceService;
import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * A managing client could create, update and draft a managed client's override, then
 * not read it: a read was checked only against the inheritance chain seen from the
 * request's client, which from the manager's own context never contains the managed
 * client. A READ now falls back to the write check, so whatever a caller may write it
 * may read, and nothing else.
 */
class AbstractOverridableAccessCheckTest {

    private static final String APP = "appbuilder";

    static class TestSchema extends AbstractSchema<TestSchema> {
        private static final long serialVersionUID = 1L;
    }

    /** The answers the security service and the inheritance chain give. */
    static class World {
        String accessType = "ANY";
        Set<String> managed = Set.of();
        boolean readAccess = true;
        boolean writeAccess = true;
        List<String> chain = List.of("SYSTEM");
    }

    static class TestService extends AbstractSchemaService<TestSchema, IOverridableDataRepository<TestSchema>> {

        TestService(World w) {
            super(TestSchema.class, null, null);
            this.securityService = new FeignAuthenticationService() {

                @Override
                public Mono<App> getAppExplicitInfoByCode(String appCode) {
                    return Mono.just(new App().setAppCode(appCode).setClientCode("SYSTEM")
                            .setAppAccessType(w.accessType));
                }

                @Override
                public Mono<Boolean> doesClientManageClientCode(String managingClientCode, String clientCode) {
                    return Mono.just(w.managed.contains(clientCode));
                }

                @Override
                public Mono<Boolean> hasReadAccess(String appCode, String clientCode) {
                    return Mono.just(w.readAccess);
                }

                @Override
                public Mono<Boolean> hasWriteAccess(String appCode, String clientCode) {
                    return Mono.just(w.writeAccess);
                }
            };
            this.inheritanceService = new InheritanceService() {

                @Override
                public Mono<List<String>> order(String appCode, String urlClientCode, String clientCode) {
                    return Mono.just(w.chain);
                }
            };
        }

        @Override
        public String getAccessCheckName() {
            return "Schema";
        }
    }

    private static ContextAuthentication system(String... authorities) {
        ContextUser user = new ContextUser().setStringAuthorities(List.of(authorities));
        return new ContextAuthentication().setUser(user).setClientCode("SYSTEM").setUrlClientCode("SYSTEM");
    }

    private static void check(World w, ContextAuthentication ca, String method, String clientCode,
            boolean writeCheck, boolean expected) {
        StepVerifier.create(new TestService(w).accessCheck(ca, method, APP, clientCode, writeCheck))
                .assertNext(r -> assertEquals(expected, r, Map.of("method", method, "client", clientCode).toString()))
                .verifyComplete();
    }

    @Test
    void aManagerReadsAManagedClientsOverride() {
        World w = new World();
        w.managed = Set.of("FIN");
        check(w, system("Authorities.Schema_READ"), "READ", "FIN", false, true);
    }

    @Test
    void aManagerWithoutWriteAccessStillCannotReadIt() {
        World w = new World();
        w.managed = Set.of("FIN");
        w.writeAccess = false;
        check(w, system("Authorities.Schema_READ"), "READ", "FIN", false, false);
    }

    @Test
    void aClientNotManagedIsStillRefused() {
        World w = new World();
        w.managed = Set.of("FIN");
        check(w, system("Authorities.Schema_READ"), "READ", "AKASH", false, false);
    }

    @Test
    void theReadAuthorityIsStillRequired() {
        World w = new World();
        w.managed = Set.of("FIN");
        check(w, system("Authorities.Schema_UPDATE"), "READ", "FIN", false, false);
    }

    @Test
    void theChainStillAnswersFirst() {
        World w = new World();
        w.chain = List.of("SYSTEM", "FIN");
        w.writeAccess = false;
        check(w, system("Authorities.Schema_READ"), "READ", "FIN", false, true);
    }

    @Test
    void onlyAReadFallsBack() {
        World w = new World();
        w.managed = Set.of("FIN");
        check(w, system("Authorities.Schema_CREATE"), "CREATE", "FIN", false, false);
    }

    @Test
    void anExplicitAppKeepsItsRule() {
        World w = new World();
        w.accessType = "EXPLICIT";
        w.managed = Set.of("FIN");
        check(w, system("Authorities.Schema_READ"), "READ", "FIN", false, false);
    }
}
