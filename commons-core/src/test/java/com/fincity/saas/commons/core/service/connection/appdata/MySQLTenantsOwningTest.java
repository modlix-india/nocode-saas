package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.service.StorageService;

import reactor.core.publisher.Mono;

/**
 * Which tenants a reconcile of one table may touch.
 *
 * A client that forked a storage has its own document with its own uniqueName, so its
 * database can hold the base's table too (built before the fork) without that table
 * being anybody's. The fan-out used to resolve the fork's columns and apply them to the
 * base table there, which quietly gave an orphan the client's fields.
 */
@DisplayName("Reconcile: tenants that own the table")
class MySQLTenantsOwningTest {

    private static final String APP = "app";
    private static final String BASE_TABLE = "app_system_d_base";

    /** Answers readForTenant from a map keyed by client code; a missing key reads empty. */
    private static final class Storages extends StorageService {

        private final Map<String, Object> byClient;

        Storages(Map<String, Object> byClient) {
            super(null, null, null, null);
            this.byClient = byClient;
        }

        @Override
        public Mono<Storage> readForTenant(String name, String appCode, String clientCode) {
            Object o = this.byClient.get(clientCode);
            if (o instanceof RuntimeException e) return Mono.error(e);
            return Mono.justOrEmpty((Storage) o);
        }
    }

    private static Storage storage(String client, String uniqueName) {
        Storage s = new Storage().setUniqueName(uniqueName);
        s.setName("deal").setAppCode(APP).setClientCode(client);
        return s;
    }

    private static List<String> owning(Map<String, Object> byClient, List<String> tenants) {
        MySQLAppDataService service = new MySQLAppDataService(new Storages(byClient), null, null, null, null, null, null);
        return service.tenantsOwning(APP, storage("SYSTEM", BASE_TABLE), BASE_TABLE, tenants).block();
    }

    @Test
    @DisplayName("a tenant that resolves to the base table is reconciled")
    void baseTenantKept() {
        assertEquals(List.of("FIN_app"), owning(Map.of("FIN", storage("SYSTEM", BASE_TABLE)), List.of("FIN_app")));
    }

    @Test
    @DisplayName("a tenant whose fork has its own table is left out, on both surfaces")
    void forkTenantSkipped() {
        Map<String, Object> m = Map.of(
                "FIN", storage("SYSTEM", BASE_TABLE),
                "FIN2", storage("FIN2", "app_fin2_dea_fork"));

        assertEquals(
                List.of("FIN_app", "FIN_app" + IAppDataService.DRAFT_DB_SUFFIX),
                owning(m, List.of("FIN_app", "FIN2_app", "FIN_app" + IAppDataService.DRAFT_DB_SUFFIX,
                        "FIN2_app" + IAppDataService.DRAFT_DB_SUFFIX)));
    }

    @Test
    @DisplayName("a tenant whose definition does not resolve is kept, so it is still reported as unresolved")
    void unresolvedKept() {
        Map<String, Object> m = Map.of("BAD", new IllegalStateException("no definition"));

        assertEquals(List.of("BAD_app", "NONE_app"), owning(m, List.of("BAD_app", "NONE_app")));
    }
}
