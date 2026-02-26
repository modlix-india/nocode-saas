package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.service.UserService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("User Sub-Org Access Restriction Tests")
class UserSubOrgAccessIntegrationTest extends AbstractIntegrationTest {

    private static final List<String> NON_OWNER_AUTHORITIES = List.of(
            "Authorities.User_READ",
            "Authorities.User_CREATE",
            "Authorities.User_UPDATE",
            "Authorities.User_DELETE",
            "Authorities.Logged_IN");

    private static final List<String> OWNER_AUTHORITIES = List.of(
            "Authorities.ROLE_Owner",
            "Authorities.User_READ",
            "Authorities.User_CREATE",
            "Authorities.User_UPDATE",
            "Authorities.User_DELETE",
            "Authorities.Logged_IN");

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        setupMockBeans();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_past_passwords WHERE USER_ID > 1").then())
                .then(databaseClient.sql("UPDATE security_user SET REPORTING_TO = NULL WHERE ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Mono<ULong> insertUserWithReportingTo(
            ULong clientId, String userName, String email, ULong reportingTo) {
        return databaseClient.sql(
                "INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, FIRST_NAME, LAST_NAME, " +
                        "PASSWORD, PASSWORD_HASHED, STATUS_CODE, REPORTING_TO) " +
                        "VALUES (:clientId, :userName, :email, 'Test', 'User', 'fincity@123', false, 'ACTIVE', :reportingTo)")
                .bind("clientId", clientId.longValue())
                .bind("userName", userName)
                .bind("email", email)
                .bind("reportingTo", reportingTo.longValue())
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one();
    }

    /**
     * Creates: BUS client, hierarchy, manager user, sub1 and sub2 (reporting to manager),
     * outsider user (same client, not in manager's sub-org).
     * Returns ULong[]{clientId, managerId, sub1Id, sub2Id, outsiderId}.
     */
    private Mono<ULong[]> setupSubOrgFixture(String clientCode) {
        return insertTestClient(clientCode, "Sub-Org Test Client", "BUS")
                .flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
                        .then(insertTestUser(clientId, "mgr_" + clientCode, "mgr_" + clientCode + "@test.com",
                                "fincity@123"))
                        .flatMap(managerId -> insertClientManager(clientId, managerId)
                                .then(insertUserWithReportingTo(
                                        clientId, "sub1_" + clientCode, "sub1_" + clientCode + "@test.com", managerId))
                                .flatMap(sub1Id -> insertUserWithReportingTo(
                                        clientId, "sub2_" + clientCode,
                                        "sub2_" + clientCode + "@test.com", managerId)
                                        .flatMap(sub2Id -> insertTestUser(
                                                clientId, "out_" + clientCode,
                                                "out_" + clientCode + "@test.com", "fincity@123")
                                                .map(outsiderId -> new ULong[] {
                                                        clientId, managerId, sub1Id, sub2Id, outsiderId
                                                })))));
    }

    // ─── readPageFilter ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("readPageFilter")
    class ReadPageFilterTests {

        @Test
        @DisplayName("non-owner sees only their sub-org users")
        void nonOwner_returnsOnlySubOrgUsers() {
            Mono<Page<User>> result = setupSubOrgFixture("SUBORG1")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong sub1Id = ids[2];
                        ULong sub2Id = ids[3];

                        ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG1", NON_OWNER_AUTHORITIES);

                        return userService
                                .readPageFilter(PageRequest.of(0, 20), null)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth))
                                .doOnNext(page -> {
                                    Set<ULong> returnedIds = page.getContent().stream()
                                            .map(User::getId)
                                            .collect(Collectors.toSet());
                                    assertThat(returnedIds).contains(managerId, sub1Id, sub2Id);
                                    assertThat(returnedIds).doesNotContain(ids[4]); // outsider
                                });
                    });

            StepVerifier.create(result)
                    .assertNext(page -> assertThat(page.getContent()).isNotEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("owner sees all users in client")
        void owner_returnsAllUsers() {
            Mono<Page<User>> result = setupSubOrgFixture("SUBORG2")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];

                        ContextAuthentication ownerAuth = TestDataFactory.createBusinessAuth(
                                ids[1], clientId, "SUBORG2", OWNER_AUTHORITIES);

                        return userService
                                .readPageFilter(PageRequest.of(0, 20), null)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ownerAuth))
                                .doOnNext(page -> {
                                    Set<ULong> returnedIds = page.getContent().stream()
                                            .map(User::getId)
                                            .collect(Collectors.toSet());
                                    // All 4 test users must be present (ids[0] is clientId, skip it)
                                    for (int i = 1; i < ids.length; i++) {
                                        assertThat(returnedIds).contains(ids[i]);
                                    }
                                });
                    });

            StepVerifier.create(result)
                    .assertNext(page -> assertThat(page.getContent().size()).isGreaterThanOrEqualTo(4))
                    .verifyComplete();
        }
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("non-owner can create with reportingTo in sub-org")
        void nonOwner_reportingToInSubOrg_succeeds() {
            Mono<User> result = setupSubOrgFixture("SUBORG3")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong sub1Id = ids[2];

                        ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG3", NON_OWNER_AUTHORITIES);

                        User newUser = new User();
                        newUser.setClientId(clientId);
                        newUser.setUserName("newuser_suborg3");
                        newUser.setEmailId("newuser_suborg3@test.com");
                        newUser.setFirstName("New");
                        newUser.setLastName("User");
                        newUser.setStatusCode(SecurityUserStatusCode.ACTIVE);
                        newUser.setReportingTo(sub1Id); // reporting to someone in sub-org

                        return userService.create(newUser)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth));
                    });

            StepVerifier.create(result)
                    .assertNext(user -> assertThat(user.getId()).isNotNull())
                    .verifyComplete();
        }

        @Test
        @DisplayName("non-owner cannot create with reportingTo outside sub-org")
        void nonOwner_reportingToOutsideSubOrg_fails() {
            Mono<User> result = setupSubOrgFixture("SUBORG4")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong outsiderId = ids[4];

                        ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG4", NON_OWNER_AUTHORITIES);

                        User newUser = new User();
                        newUser.setClientId(clientId);
                        newUser.setUserName("newuser_suborg4");
                        newUser.setEmailId("newuser_suborg4@test.com");
                        newUser.setFirstName("New");
                        newUser.setLastName("User");
                        newUser.setStatusCode(SecurityUserStatusCode.ACTIVE);
                        newUser.setReportingTo(outsiderId); // reporting to someone outside sub-org

                        return userService.create(newUser)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth));
                    });

            StepVerifier.create(result)
                    .expectError(GenericException.class)
                    .verify();
        }

        @Test
        @DisplayName("non-owner can create with null reportingTo")
        void nonOwner_nullReportingTo_succeeds() {
            Mono<User> result = setupSubOrgFixture("SUBORG5")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];

                        ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG5", NON_OWNER_AUTHORITIES);

                        User newUser = new User();
                        newUser.setClientId(clientId);
                        newUser.setUserName("newuser_suborg5");
                        newUser.setEmailId("newuser_suborg5@test.com");
                        newUser.setFirstName("New");
                        newUser.setLastName("User");
                        newUser.setStatusCode(SecurityUserStatusCode.ACTIVE);
                        // reportingTo left null

                        return userService.create(newUser)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth));
                    });

            StepVerifier.create(result)
                    .assertNext(user -> assertThat(user.getId()).isNotNull())
                    .verifyComplete();
        }

        @Test
        @DisplayName("owner can create with any reportingTo")
        void owner_anyReportingTo_succeeds() {
            Mono<User> result = setupSubOrgFixture("SUBORG6")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong outsiderId = ids[4];

                        ContextAuthentication ownerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG6", OWNER_AUTHORITIES);

                        User newUser = new User();
                        newUser.setClientId(clientId);
                        newUser.setUserName("newuser_suborg6");
                        newUser.setEmailId("newuser_suborg6@test.com");
                        newUser.setFirstName("New");
                        newUser.setLastName("User");
                        newUser.setStatusCode(SecurityUserStatusCode.ACTIVE);
                        newUser.setReportingTo(outsiderId); // outsider is fine for owner

                        return userService.create(newUser)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ownerAuth));
                    });

            StepVerifier.create(result)
                    .assertNext(user -> assertThat(user.getId()).isNotNull())
                    .verifyComplete();
        }
    }

    // ─── update(key, fields) ──────────────────────────────────────────────────

    @Nested
    @DisplayName("update(key, fields)")
    class UpdateMapTests {

        @Test
        @DisplayName("non-owner can update a user in their sub-org")
        void nonOwner_targetInSubOrg_succeeds() {
            Mono<User> result = setupSubOrgFixture("SUBORG7")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong sub1Id = ids[2];

                        ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG7", NON_OWNER_AUTHORITIES);

                        return userService
                                .update(sub1Id, Map.of("firstName", "Updated"))
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth));
                    });

            StepVerifier.create(result)
                    .assertNext(user -> assertThat(user.getFirstName()).isEqualTo("Updated"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("non-owner cannot update a user outside their sub-org")
        void nonOwner_targetOutsideSubOrg_fails() {
            Mono<User> result = setupSubOrgFixture("SUBORG8")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong outsiderId = ids[4];

                        ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG8", NON_OWNER_AUTHORITIES);

                        return userService
                                .update(outsiderId, Map.of("firstName", "Hacked"))
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth));
                    });

            StepVerifier.create(result)
                    .expectError(GenericException.class)
                    .verify();
        }
    }

    // ─── update(entity) ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("update(entity)")
    class UpdateEntityTests {

        @Test
        @DisplayName("non-owner can update an entity in their sub-org")
        void nonOwner_targetInSubOrg_succeeds() {
            Mono<User> result = setupSubOrgFixture("SUBORG9")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong sub2Id = ids[3];

                        ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG9", NON_OWNER_AUTHORITIES);

                        return userService.readInternal(sub2Id)
                                .flatMap(user -> {
                                    user.setFirstName("UpdatedEntity");
                                    return userService.update(user);
                                })
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth));
                    });

            StepVerifier.create(result)
                    .assertNext(user -> assertThat(user.getFirstName()).isEqualTo("UpdatedEntity"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("non-owner cannot update an entity outside their sub-org")
        void nonOwner_targetOutsideSubOrg_fails() {
            Mono<User> result = setupSubOrgFixture("SUBORG10")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong outsiderId = ids[4];

                        ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG10", NON_OWNER_AUTHORITIES);

                        return userService.readInternal(outsiderId)
                                .flatMap(user -> {
                                    user.setFirstName("Hacked");
                                    return userService.update(user);
                                })
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth));
                    });

            StepVerifier.create(result)
                    .expectError(GenericException.class)
                    .verify();
        }
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("non-owner can delete a user in their sub-org")
        void nonOwner_targetInSubOrg_succeeds() {
            Mono<String> result = setupSubOrgFixture("SUBORG11")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong sub2Id = ids[3];

                        ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG11", NON_OWNER_AUTHORITIES);

                        return userService.delete(sub2Id)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth))
                                .flatMap(count -> {
                                    assertThat(count).isEqualTo(1);
                                    return databaseClient
                                            .sql("SELECT STATUS_CODE FROM security_user WHERE ID = :id")
                                            .bind("id", sub2Id.longValue())
                                            .map(row -> row.get("STATUS_CODE", String.class))
                                            .one();
                                });
                    });

            StepVerifier.create(result)
                    .assertNext(status -> assertThat(status).isEqualTo("DELETED"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("non-owner cannot delete a user outside their sub-org")
        void nonOwner_targetOutsideSubOrg_fails() {
            Mono<Integer> result = setupSubOrgFixture("SUBORG12")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong outsiderId = ids[4];

                        ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG12", NON_OWNER_AUTHORITIES);

                        return userService.delete(outsiderId)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth));
                    });

            StepVerifier.create(result)
                    .expectError(GenericException.class)
                    .verify();
        }

        @Test
        @DisplayName("owner can delete any user in their client")
        void owner_anyTarget_succeeds() {
            Mono<String> result = setupSubOrgFixture("SUBORG13")
                    .flatMap(ids -> {
                        ULong clientId = ids[0];
                        ULong managerId = ids[1];
                        ULong outsiderId = ids[4];

                        ContextAuthentication ownerAuth = TestDataFactory.createBusinessAuth(
                                managerId, clientId, "SUBORG13", OWNER_AUTHORITIES);

                        return userService.delete(outsiderId)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ownerAuth))
                                .flatMap(count -> databaseClient
                                        .sql("SELECT STATUS_CODE FROM security_user WHERE ID = :id")
                                        .bind("id", outsiderId.longValue())
                                        .map(row -> row.get("STATUS_CODE", String.class))
                                        .one());
                    });

            StepVerifier.create(result)
                    .assertNext(status -> assertThat(status).isEqualTo("DELETED"))
                    .verifyComplete();
        }
    }

    // ─── self-update ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("non-owner can update their own profile (self is always in sub-org)")
    void manager_canUpdateOwnProfile() {
        Mono<User> result = setupSubOrgFixture("SUBORG14")
                .flatMap(ids -> {
                    ULong clientId = ids[0];
                    ULong managerId = ids[1];

                    ContextAuthentication managerAuth = TestDataFactory.createBusinessAuth(
                            managerId, clientId, "SUBORG14", NON_OWNER_AUTHORITIES);

                    return userService
                            .update(managerId, Map.of("firstName", "SelfUpdated"))
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(managerAuth));
                });

        StepVerifier.create(result)
                .assertNext(user -> assertThat(user.getFirstName()).isEqualTo("SelfUpdated"))
                .verifyComplete();
    }
}
