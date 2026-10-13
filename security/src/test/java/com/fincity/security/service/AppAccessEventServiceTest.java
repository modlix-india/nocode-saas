package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.jooq.types.UByte;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.security.jooq.tables.records.SecurityAppAccessRecord;
import com.fincity.security.service.AppAccessEventService.Action;
import com.fincity.security.service.AppAccessEventService.Level;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
class AppAccessEventServiceTest extends AbstractServiceUnitTest {

	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong CLIENT_ID = ULong.valueOf(7);
	private static final ULong USER_ID = ULong.valueOf(42);

	@Mock
	private ClientService clientService;

	@Mock
	private EventCreationService ecService;

	private AppAccessEventService service;

	@BeforeEach
	void setUp() {
		service = new AppAccessEventService(clientService, ecService);
	}

	@ParameterizedTest
	@CsvSource({
			"NONE, READ, READ_ADDED",
			"NONE, EDIT, EDIT_ADDED",
			"READ, EDIT, UPGRADED_TO_EDIT",
			"EDIT, READ, DOWNGRADED_TO_READ",
			"READ, NONE, REMOVED",
			"EDIT, NONE, REMOVED" })
	void action_FromPreviousAndCurrent(Level previous, Level current, Action expected) {
		assertEquals(expected, Action.of(previous, current));
	}

	@ParameterizedTest
	@CsvSource({ "NONE", "READ", "EDIT" })
	void action_NoChange_IsNull(Level level) {
		assertNull(Action.of(level, level));
	}

	@Test
	void level_FromAccessRow() {
		SecurityAppAccessRecord edit = new SecurityAppAccessRecord();
		edit.setEditAccess(UByte.valueOf(1));
		SecurityAppAccessRecord read = new SecurityAppAccessRecord();
		read.setEditAccess(UByte.valueOf(0));

		assertEquals(Level.EDIT, Level.of(edit));
		assertEquals(Level.READ, Level.of(read));
		assertEquals(Level.NONE, Level.of((SecurityAppAccessRecord) null));
	}

	@Test
	void raise_PublishesForTheAppAndTheAffectedClient() {
		setupSecurityContext(TestDataFactory.createBusinessAuth(USER_ID, ULong.valueOf(1), "SYSTEM",
				List.of("Authorities.Logged_IN")));
		when(clientService.getClientTypeNCodeNClientLevel(CLIENT_ID))
				.thenReturn(Mono.just(Tuples.of("BUS", "AKASH", "CUSTOMER")));
		when(ecService.createEvent(any())).thenReturn(Mono.just(true));

		StepVerifier.create(service.raise("leadzumptwo", APP_ID, CLIENT_ID, Level.READ, Level.EDIT))
				.expectNext(true)
				.verifyComplete();

		ArgumentCaptor<EventQueObject> captor = ArgumentCaptor.forClass(EventQueObject.class);
		verify(ecService).createEvent(captor.capture());
		EventQueObject event = captor.getValue();

		assertEquals(AppAccessEventService.APP_ACCESS_CHANGED, event.getEventName());
		assertEquals("leadzumptwo", event.getAppCode());
		assertEquals("AKASH", event.getClientCode());
		assertEquals(Map.of(
				"action", "UPGRADED_TO_EDIT",
				"previous", "READ",
				"current", "EDIT",
				"appCode", "leadzumptwo",
				"appId", "100",
				"clientCode", "AKASH",
				"clientId", "7",
				"clientTypeCode", "BUS",
				"clientLevelType", "CUSTOMER",
				"changedBy", "42"), event.getData());
	}

	@Test
	void raise_NobodyLoggedIn_ChangedByIsBlank() {
		setupEmptySecurityContext();
		when(clientService.getClientTypeNCodeNClientLevel(CLIENT_ID))
				.thenReturn(Mono.just(Tuples.of("BUS", "AKASH", "CUSTOMER")));
		when(ecService.createEvent(any())).thenReturn(Mono.just(true));

		StepVerifier.create(service.raise("leadzumptwo", APP_ID, CLIENT_ID, Level.NONE, Level.READ))
				.expectNext(true)
				.verifyComplete();

		ArgumentCaptor<EventQueObject> captor = ArgumentCaptor.forClass(EventQueObject.class);
		verify(ecService).createEvent(captor.capture());
		assertEquals("", captor.getValue().getData().get("changedBy"));
		assertEquals("READ_ADDED", captor.getValue().getData().get("action"));
	}

	@Test
	void raise_NoChange_PublishesNothing() {
		StepVerifier.create(service.raise("leadzumptwo", APP_ID, CLIENT_ID, Level.EDIT, Level.EDIT))
				.expectNext(false)
				.verifyComplete();

		verifyNoInteractions(clientService, ecService);
	}

	/** The access change has already been written; a publish failure must not undo the caller's success. */
	@Test
	void raise_PublishFails_SwallowsTheError() {
		setupEmptySecurityContext();
		when(clientService.getClientTypeNCodeNClientLevel(CLIENT_ID))
				.thenReturn(Mono.just(Tuples.of("BUS", "AKASH", "CUSTOMER")));
		when(ecService.createEvent(any())).thenReturn(Mono.error(new IllegalStateException("broker down")));

		StepVerifier.create(service.raise("leadzumptwo", APP_ID, CLIENT_ID, Level.EDIT, Level.NONE))
				.expectNext(false)
				.verifyComplete();
	}

	@Test
	void raise_UnknownClient_PublishesNothing() {
		when(clientService.getClientTypeNCodeNClientLevel(CLIENT_ID)).thenReturn(Mono.empty());

		StepVerifier.create(service.raise("leadzumptwo", APP_ID, CLIENT_ID, Level.NONE, Level.EDIT))
				.expectNext(false)
				.verifyComplete();

		verifyNoInteractions(ecService);
	}
}
