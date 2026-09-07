package com.modlix.saas.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.bind.annotation.RequestHeader;

import com.modlix.saas.commons2.mq.notifications.NotificationQueObject;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.model.NotificationUser;
import com.modlix.saas.commons2.security.model.UsersListRequest;
import com.modlix.saas.notification.feign.IFeignCoreService;
import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.model.CoreNotification.NotificationTemplate;
import com.modlix.saas.notification.model.NotificationConnectionDetails;
import com.modlix.saas.notification.service.email.EmailService;

/**
 * The consuming half of the draft marker: definitions follow the surface,
 * recipients do not.
 *
 * The sender runs off a queue message with no inbound request, so the only thing
 * that can tell it which surface raised the notification is the flag on the
 * message. What it does with that flag is the point of these tests: it must reach
 * the two core DEFINITION lookups and nothing else. Emails still go to real people
 * out of the real user directory, which is a decision, so a test has to pin it or
 * a later "consistency" change will quietly start routing recipients too.
 */
@DisplayName("Draft marker in the notification sender")
class NotificationDraftHeaderTest {

    private static final String CHANNEL_IN_APP = "inapp";

    private IFeignCoreService coreService;
    private IFeignSecurityService securityService;
    private EmailService emailService;
    private InAppNotificationService inAppService;
    private NotificationSendService sendService;

    @BeforeEach
    void setUp() {

        this.coreService = Mockito.mock(IFeignCoreService.class);
        this.securityService = Mockito.mock(IFeignSecurityService.class);
        this.emailService = Mockito.mock(EmailService.class);
        this.inAppService = Mockito.mock(InAppNotificationService.class);

        // Only the in-app channel: email would need a real Connection, and which
        // channel fires is not what these tests are about.
        NotificationConnectionDetails connection = new NotificationConnectionDetails(true, null);

        CoreNotification notification = new CoreNotification();
        notification.setChannelTemplates(Map.of(CHANNEL_IN_APP, new NotificationTemplate()));

        Mockito.when(this.coreService.getNotificationConnection(Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(connection);
        Mockito.when(this.coreService.getNotification(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any())).thenReturn(notification);
        Mockito.when(this.securityService.getUsersForNotification(Mockito.any()))
                .thenReturn(List.of(new NotificationUser()));

        this.sendService = new NotificationSendService(this.securityService, this.coreService, this.emailService,
                this.inAppService);
    }

    private NotificationQueObject message(boolean draft) {
        return new NotificationQueObject()
                .setAppCode("testapp")
                .setClientCode("SYSTEM")
                .setUrlClientCode("SYSTEM")
                .setTargetType("User Id")
                .setTargetId(BigInteger.ONE)
                .setNotificationName("testNotification")
                .setNotificationCategory("SYSTEM")
                .setConnectionName("appNotification")
                .setDraft(draft);
    }

    private String capturedConnectionDraft() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(this.coreService).getNotificationConnection(Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), captor.capture());
        return captor.getValue();
    }

    private String capturedNotificationDraft() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(this.coreService).getNotification(Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a draft message resolves both definitions on the draft surface")
    void draftMessageAsksCoreForDrafts() {

        this.sendService.sendNotification(this.message(true));

        assertEquals("true", this.capturedConnectionDraft(),
                "the connection was resolved from the live surface, so a draft would use live credentials");
        assertEquals("true", this.capturedNotificationDraft(),
                "the notification definition was resolved from the live surface");
    }

    @Test
    @DisplayName("a live message sends no header at all")
    void liveMessageSendsNoHeader() {

        this.sendService.sendNotification(this.message(false));

        // Null, not "false": the header is `required = false`, so a null value omits
        // it entirely and every existing live call goes out byte-identical to before.
        assertNull(this.capturedConnectionDraft(), "a live notification must not carry the draft header");
        assertNull(this.capturedNotificationDraft(), "a live notification must not carry the draft header");
    }

    @Test
    @DisplayName("recipients come from the real user directory on both surfaces")
    void recipientsIgnoreTheMarker() {

        this.sendService.sendNotification(this.message(true));

        ArgumentCaptor<UsersListRequest> captor = ArgumentCaptor.forClass(UsersListRequest.class);
        Mockito.verify(this.securityService).getUsersForNotification(captor.capture());

        // The request that resolves recipients has no surface dimension at all, and
        // that is deliberate: a draft notification reaches the same real people.
        // Asserted on the type rather than a value so that adding one would fail
        // here rather than silently changing who gets email.
        assertTrue(
                java.util.Arrays.stream(UsersListRequest.class.getDeclaredFields())
                        .noneMatch(f -> f.getName().toLowerCase().contains("draft")),
                "UsersListRequest grew a draft dimension; recipients were meant to stay on the real directory");

        assertEquals("testapp", captor.getValue().getAppCode());
    }

    /**
     * Guards the reason the header is set by hand on the two core methods rather
     * than in NotificationConfiguration's RequestInterceptor. An interceptor applies
     * to every Feign client in the module, so it would have put x-draft on the
     * security calls too. If someone later moves it there, this fails.
     */
    @Test
    @DisplayName("only the core definition lookups declare the header")
    void onlyCoreLookupsDeclareTheHeader() {

        assertEquals(2, countDraftHeaderParams(IFeignCoreService.class),
                "expected exactly the two definition lookups to take the draft header");
        assertEquals(0, countDraftHeaderParams(IFeignSecurityService.class),
                "a security call declared the draft header; recipient resolution must stay on the live directory");
    }

    private static long countDraftHeaderParams(Class<?> type) {
        long count = 0;
        for (Method m : type.getDeclaredMethods())
            for (var p : m.getParameters()) {
                RequestHeader h = p.getAnnotation(RequestHeader.class);
                if (h == null)
                    continue;
                String name = h.name().isEmpty() ? h.value() : h.name();
                if ("x-draft".equalsIgnoreCase(name))
                    count++;
            }
        return count;
    }
}
