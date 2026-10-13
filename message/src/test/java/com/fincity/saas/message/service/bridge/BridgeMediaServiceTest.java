package com.fincity.saas.message.service.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.feign.IFeignFileService;
import com.fincity.saas.message.oserver.files.model.FileDetail;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Where an attachment lands, and whether it carries a lifetime, by who owns the number: core-owned
 * numbers store under the app's own folder with no lifetime (its retention policy decides);
 * entity-processor numbers keep the old tree and the thirty days, unchanged until leadzump retires.
 */
class BridgeMediaServiceTest {

    private static final int THIRTY_DAYS_IN_MINUTES = 30 * 24 * 60;
    private static final ByteBuffer BODY = ByteBuffer.wrap(new byte[] {1, 2, 3});

    private WhatsappPhoneNumberDAO dao;
    private IFeignFileService files;
    private BridgeMediaService service;

    @BeforeEach
    void setUp() {
        dao = mock(WhatsappPhoneNumberDAO.class);
        files = mock(IFeignFileService.class);
        service = new BridgeMediaService(dao, files);
        when(files.create(anyString(), anyString(), anyBoolean(), anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(new FileDetail()));
        when(files.downloadFile(anyString(), anyString())).thenReturn(Mono.just(BODY));
    }

    private void session(String appCode, String owner) {
        WhatsappPhoneNumber n = new WhatsappPhoneNumber();
        n.setAppCode(appCode);
        n.setClientCode("FIN");
        n.setOwnerService(owner);
        when(dao.getBySessionIdInternal("s1")).thenReturn(Mono.just(n));
    }

    @Test
    void coreOwnedIncomingGoesUnderTheAppFolderWithNoLifetime() {
        session("leadzumptwo", "core");

        StepVerifier.create(service.store("s1", "wamid.1", "919000000001", "image/jpeg", null, false, BODY))
                .expectNextCount(1)
                .verifyComplete();

        verify(files).create(eq("secured"), eq("FIN"), eq(false),
                eq("/leadzumptwo/whatsapp/incoming/919000000001"), eq("wamid.1.jpeg"), isNull(), any());
    }

    @Test
    void coreOwnedOutgoingGoesUnderTheAppFolderWithNoLifetime() {
        session("leadzumptwo", "core");

        StepVerifier.create(service.store("s1", "wamid.2", "919000000001", "application/pdf", "a.pdf", true, BODY))
                .expectNextCount(1)
                .verifyComplete();

        verify(files).create(eq("secured"), eq("FIN"), eq(false),
                eq("/leadzumptwo/whatsapp/outgoing/919000000001"), eq("wamid.2.pdf"), isNull(), any());
    }

    @Test
    void entityProcessorOwnedKeepsTheOldTreeAndThirtyDays() {
        session("leadzump", "entity-processor");

        StepVerifier.create(service.store("s1", "wamid.3", "919000000001", "image/png", null, false, BODY))
                .expectNextCount(1)
                .verifyComplete();

        verify(files).create(eq("secured"), eq("FIN"), eq(false),
                eq("/whatsapp/leadzump/incoming/919000000001"), eq("wamid.3.png"), eq(THIRTY_DAYS_IN_MINUTES),
                any());
    }

    @Test
    void unroutedNumberKeepsTheOldTreeAndThirtyDays() {
        session("leadzump", null);

        StepVerifier.create(service.store("s1", "wamid.4", null, "image/png", null, true, BODY))
                .expectNextCount(1)
                .verifyComplete();

        verify(files).create(eq("secured"), eq("FIN"), eq(false),
                eq("/whatsapp/leadzump/outgoing/unknown"), eq("wamid.4.png"), eq(THIRTY_DAYS_IN_MINUTES), any());
    }

    @Test
    void appCodeIsSanitisedInTheNewLayout() {
        session("lead/../zump two", "core");

        StepVerifier.create(service.store("s1", "wamid.5", "91 900", "image/png", null, false, BODY))
                .expectNextCount(1)
                .verifyComplete();

        verify(files).create(eq("secured"), eq("FIN"), eq(false),
                eq("/lead_.._zump_two/whatsapp/incoming/91_900"), eq("wamid.5.png"), isNull(), any());
    }

    @Test
    void coreOwnedAvatarGoesUnderTheAppFolderWithNoLifetime() {
        session("leadzumptwo", "core");

        StepVerifier.create(service.storeAvatar("s1", "919000000001", BODY))
                .expectNextCount(1)
                .verifyComplete();

        verify(files).create(eq("secured"), eq("FIN"), eq(true),
                eq("/leadzumptwo/whatsapp/avatars"), eq("919000000001.jpg"), isNull(), any());
    }

    @Test
    void entityProcessorAvatarKeepsTheOldTreeWithNoLifetime() {
        session("leadzump", "entity-processor");

        StepVerifier.create(service.storeAvatar("s1", "919000000001", BODY))
                .expectNextCount(1)
                .verifyComplete();

        verify(files).create(eq("secured"), eq("FIN"), eq(true),
                eq("/whatsapp/leadzump/avatars"), eq("919000000001.jpg"), isNull(), any());
    }

    @Test
    void coreOwnedReadsBothTheNewAndTheOldTree() {
        session("leadzumptwo", "core");

        StepVerifier.create(service.fetch("s1", "/leadzumptwo/whatsapp/outgoing/42/a.pdf"))
                .expectNextCount(1)
                .verifyComplete();
        verify(files).downloadFile("secured", "FIN/leadzumptwo/whatsapp/outgoing/42/a.pdf");

        StepVerifier.create(service.fetch("s1", "whatsapp/leadzumptwo/outgoing/42/b.pdf"))
                .expectNextCount(1)
                .verifyComplete();
        verify(files).downloadFile("secured", "FIN/whatsapp/leadzumptwo/outgoing/42/b.pdf");
    }

    @Test
    void entityProcessorCannotReadTheNewTreeAndNobodyReadsAnotherApp() {
        session("leadzump", "entity-processor");

        StepVerifier.create(service.fetch("s1", "/leadzump/whatsapp/outgoing/42/a.pdf")).verifyComplete();
        StepVerifier.create(service.fetch("s1", "/whatsapp/otherapp/outgoing/42/a.pdf")).verifyComplete();

        session("leadzumptwo", "core");
        StepVerifier.create(service.fetch("s1", "/otherapp/whatsapp/outgoing/42/a.pdf")).verifyComplete();
        StepVerifier.create(service.fetch("s1", "/leadzumptwo/whatsapp/../../x")).verifyComplete();

        verify(files, never()).downloadFile(anyString(), anyString());
    }

    @Test
    void mediaRootByOwner() {
        WhatsappPhoneNumber n = new WhatsappPhoneNumber();
        n.setAppCode("leadzumptwo");
        n.setOwnerService("core");
        assertEquals("/leadzumptwo/whatsapp", BridgeMediaService.mediaRoot(n));
        n.setOwnerService("entity-processor");
        assertEquals("/whatsapp/leadzumptwo", BridgeMediaService.mediaRoot(n));
        n.setOwnerService(null);
        assertEquals("/whatsapp/leadzumptwo", BridgeMediaService.mediaRoot(n));
    }
}
