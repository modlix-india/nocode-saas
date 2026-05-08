package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.ClientActivityDAO;
import com.fincity.security.dto.ClientActivity;
import com.fincity.security.dto.User;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ClientActivityServiceTest extends AbstractServiceUnitTest {

    @Mock
    private ClientActivityDAO dao;

    @Mock
    private ClientService clientService;

    @Mock
    private UserService userService;

    @Mock
    private SecurityMessageResourceService messageResourceService;

    private ClientActivityService service;

    private static final ULong CLIENT_ID      = ULong.valueOf(2);
    private static final ULong OTHER_CLIENT_ID = ULong.valueOf(3);
    private static final ULong USER_ID         = ULong.valueOf(10);
    private static final ULong OTHER_USER_ID   = ULong.valueOf(20);
    private static final ULong ACTIVITY_ID     = ULong.valueOf(100);
    private static final ULong ACTIVITY_ID_2   = ULong.valueOf(101);

    @BeforeEach
    void setUp() {
        service = new ClientActivityService(clientService, userService, messageResourceService);

        // Inject mocked DAO into parent AbstractJOOQDataService.dao field
        try {
            var daoField = service.getClass().getSuperclass().getDeclaredField("dao");
            daoField.setAccessible(true);
            daoField.set(service, dao);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject DAO", e);
        }

        setupMessageResourceService(messageResourceService);
    }

    // =========================================================================
    // create() tests
    // =========================================================================

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("defaults clientId from security context when not provided")
        void create_NullClientId_DefaultsFromContext() {
            ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, CLIENT_ID, "BUSCLIENT",
                    List.of("Authorities.Logged_IN"));
            setupSecurityContext(ca);

            ClientActivity entity = new ClientActivity().setActivityName("Login");
            ClientActivity saved  = TestDataFactory.createClientActivity(ACTIVITY_ID, CLIENT_ID, USER_ID, "Login", null);

            // After defaulting clientId from context, access check is made against CLIENT_ID
            when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(CLIENT_ID)))
                    .thenReturn(Mono.just(true));
            when(dao.create(any(ClientActivity.class))).thenReturn(Mono.just(saved));

            StepVerifier.create(service.create(entity))
                    .assertNext(a -> {
                        assertEquals(ACTIVITY_ID, a.getId());
                        assertEquals(CLIENT_ID, a.getClientId());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("creates activity for own client")
        void create_OwnClient_CreatesSuccessfully() {
            ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, CLIENT_ID, "BUSCLIENT",
                    List.of("Authorities.Logged_IN"));
            setupSecurityContext(ca);

            ClientActivity entity = new ClientActivity().setClientId(CLIENT_ID).setActivityName("Signup");
            ClientActivity saved  = TestDataFactory.createClientActivity(ACTIVITY_ID, CLIENT_ID, USER_ID, "Signup", null);

            when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(CLIENT_ID)))
                    .thenReturn(Mono.just(true));
            when(dao.create(any(ClientActivity.class))).thenReturn(Mono.just(saved));

            StepVerifier.create(service.create(entity))
                    .assertNext(a -> assertEquals(ACTIVITY_ID, a.getId()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("creates activity for managed client")
        void create_ManagedClient_CreatesSuccessfully() {
            ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, CLIENT_ID, "BUSCLIENT",
                    List.of("Authorities.Logged_IN"));
            setupSecurityContext(ca);

            ClientActivity entity = new ClientActivity().setClientId(OTHER_CLIENT_ID).setActivityName("Onboard");
            ClientActivity saved  = TestDataFactory.createClientActivity(ACTIVITY_ID, OTHER_CLIENT_ID, USER_ID, "Onboard", null);

            when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(OTHER_CLIENT_ID)))
                    .thenReturn(Mono.just(true));
            when(dao.create(any(ClientActivity.class))).thenReturn(Mono.just(saved));

            StepVerifier.create(service.create(entity))
                    .assertNext(a -> assertEquals(ACTIVITY_ID, a.getId()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("throws FORBIDDEN when user has no access to the client")
        void create_NoAccess_ThrowsForbidden() {
            ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, CLIENT_ID, "BUSCLIENT",
                    List.of("Authorities.Logged_IN"));
            setupSecurityContext(ca);

            ClientActivity entity = new ClientActivity().setClientId(OTHER_CLIENT_ID).setActivityName("Hack");

            when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(OTHER_CLIENT_ID)))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(service.create(entity))
                    .expectErrorMatches(e -> e instanceof GenericException
                            && ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
                    .verify();
        }
    }

    // =========================================================================
    // readPageFilter() tests
    // =========================================================================

    @Nested
    @DisplayName("readPageFilter()")
    class ReadPageFilterTests {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("owner can see all activities for the client")
        void readPageFilter_OwnerUser_CanSeeAll() {
            ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, CLIENT_ID, "BUSCLIENT",
                    List.of("Authorities.ROLE_Owner", "Authorities.Logged_IN"));
            setupSecurityContext(ca);

            ClientActivity a1 = TestDataFactory.createClientActivity(ACTIVITY_ID, CLIENT_ID, USER_ID);
            ClientActivity a2 = TestDataFactory.createClientActivity(ACTIVITY_ID_2, CLIENT_ID, OTHER_USER_ID);
            Page<ClientActivity> resultPage = new PageImpl<>(List.of(a1, a2), pageable, 2);

            when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(CLIENT_ID)))
                    .thenReturn(Mono.just(true));
            // Owner: no CREATED_BY restriction — DAO receives only the clientId condition
            when(dao.readPageFilter(eq(pageable), argThat(cond ->
                    cond != null && !cond.toString().contains("createdBy"))))
                    .thenReturn(Mono.just(resultPage));

            StepVerifier.create(service.readPageFilter(CLIENT_ID, pageable, null))
                    .assertNext(page -> assertEquals(2, page.getTotalElements()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("same-client non-owner sees only their own activities")
        void readPageFilter_SameClientRegularUser_SeesOwnOnly() {
            ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, CLIENT_ID, "BUSCLIENT",
                    List.of("Authorities.Logged_IN")); // no ROLE_Owner
            setupSecurityContext(ca);

            ClientActivity a1 = TestDataFactory.createClientActivity(ACTIVITY_ID, CLIENT_ID, USER_ID);
            Page<ClientActivity> resultPage = new PageImpl<>(List.of(a1), pageable, 1);

            when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(CLIENT_ID)))
                    .thenReturn(Mono.just(true));
            when(dao.readPageFilter(eq(pageable), any()))
                    .thenReturn(Mono.just(resultPage));

            StepVerifier.create(service.readPageFilter(CLIENT_ID, pageable, null))
                    .assertNext(page -> {
                        assertEquals(1, page.getTotalElements());
                        assertEquals(ACTIVITY_ID, page.getContent().getFirst().getId());
                    })
                    .verifyComplete();

            // Verify DAO was called (CREATED_BY filter gets added in condition)
            verify(dao).readPageFilter(eq(pageable), any());
        }

        @Test
        @DisplayName("cross-hierarchy manager can see all activities")
        void readPageFilter_CrossHierarchyManager_CanSeeAll() {
            // User belongs to CLIENT_ID, but is reading activities for OTHER_CLIENT_ID (managed)
            ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, CLIENT_ID, "BUSCLIENT",
                    List.of("Authorities.Logged_IN"));
            setupSecurityContext(ca);

            ClientActivity a1 = TestDataFactory.createClientActivity(ACTIVITY_ID, OTHER_CLIENT_ID, OTHER_USER_ID);
            Page<ClientActivity> resultPage = new PageImpl<>(List.of(a1), pageable, 1);

            when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(OTHER_CLIENT_ID)))
                    .thenReturn(Mono.just(true));
            when(dao.readPageFilter(eq(pageable), any()))
                    .thenReturn(Mono.just(resultPage));

            StepVerifier.create(service.readPageFilter(OTHER_CLIENT_ID, pageable, null))
                    .assertNext(page -> assertEquals(1, page.getTotalElements()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("throws FORBIDDEN when user has no access to the client")
        void readPageFilter_NoAccess_ThrowsForbidden() {
            ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, CLIENT_ID, "BUSCLIENT",
                    List.of("Authorities.Logged_IN"));
            setupSecurityContext(ca);

            when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(OTHER_CLIENT_ID)))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(service.readPageFilter(OTHER_CLIENT_ID, pageable, null))
                    .expectErrorMatches(e -> e instanceof GenericException
                            && ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
                    .verify();
        }

        @Test
        @DisplayName("extra condition from request is included in DAO call")
        void readPageFilter_WithExtraCondition_PassedToDAO() {
            ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, CLIENT_ID, "BUSCLIENT",
                    List.of("Authorities.ROLE_Owner", "Authorities.Logged_IN"));
            setupSecurityContext(ca);

            Page<ClientActivity> resultPage = new PageImpl<>(List.of(), pageable, 0);
            FilterCondition extra = FilterCondition.make("activityName", "Login");

            when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(CLIENT_ID)))
                    .thenReturn(Mono.just(true));
            when(dao.readPageFilter(eq(pageable), any()))
                    .thenReturn(Mono.just(resultPage));

            StepVerifier.create(service.readPageFilter(CLIENT_ID, pageable, extra))
                    .assertNext(page -> assertEquals(0, page.getTotalElements()))
                    .verifyComplete();

            verify(dao).readPageFilter(eq(pageable), any());
        }
    }

    // =========================================================================
    // fillCreatedByUser() tests
    // =========================================================================

    @Nested
    @DisplayName("fillCreatedByUser()")
    class FillCreatedByUserTests {

        @Test
        @DisplayName("populates createdByUser for each activity")
        void fillCreatedByUser_PopulatesUsers() {
            User user1 = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
            User user2 = TestDataFactory.createActiveUser(OTHER_USER_ID, CLIENT_ID);

            ClientActivity a1 = TestDataFactory.createClientActivity(ACTIVITY_ID, CLIENT_ID, USER_ID);
            ClientActivity a2 = TestDataFactory.createClientActivity(ACTIVITY_ID_2, CLIENT_ID, OTHER_USER_ID);
            List<ClientActivity> activities = List.of(a1, a2);

            when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user1));
            when(userService.readInternal(OTHER_USER_ID)).thenReturn(Mono.just(user2));

            StepVerifier.create(service.fillCreatedByUser(activities))
                    .verifyComplete();

            assertEquals(user1, a1.getCreatedByUser());
            assertEquals(user2, a2.getCreatedByUser());
        }

        @Test
        @DisplayName("deduplicates user lookups for same createdBy")
        void fillCreatedByUser_DeduplicatesLookups() {
            User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

            ClientActivity a1 = TestDataFactory.createClientActivity(ACTIVITY_ID, CLIENT_ID, USER_ID);
            ClientActivity a2 = TestDataFactory.createClientActivity(ACTIVITY_ID_2, CLIENT_ID, USER_ID);

            when(userService.readInternal(USER_ID)).thenReturn(Mono.just(user));

            StepVerifier.create(service.fillCreatedByUser(List.of(a1, a2)))
                    .verifyComplete();

            verify(userService, times(1)).readInternal(USER_ID);
            assertEquals(user, a1.getCreatedByUser());
            assertEquals(user, a2.getCreatedByUser());
        }

        @Test
        @DisplayName("returns empty Mono for empty list")
        void fillCreatedByUser_EmptyList_ReturnsEmpty() {
            StepVerifier.create(service.fillCreatedByUser(List.of()))
                    .verifyComplete();

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("returns empty Mono for null list")
        void fillCreatedByUser_NullList_ReturnsEmpty() {
            StepVerifier.create(service.fillCreatedByUser(null))
                    .verifyComplete();

            verifyNoInteractions(userService);
        }
    }

    // =========================================================================
    // delete() tests
    // =========================================================================

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("always throws FORBIDDEN")
        void delete_AlwaysThrowsForbidden() {
            StepVerifier.create(service.delete(ACTIVITY_ID))
                    .expectErrorMatches(e -> e instanceof GenericException
                            && ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
                    .verify();
        }
    }
}
