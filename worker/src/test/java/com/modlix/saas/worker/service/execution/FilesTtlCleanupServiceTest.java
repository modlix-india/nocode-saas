package com.modlix.saas.worker.service.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.modlix.saas.worker.dto.CoreEventRaiseRequest;
import com.modlix.saas.worker.dto.FilesRetentionResult;
import com.modlix.saas.worker.dto.Task;
import com.modlix.saas.worker.feign.IFeignCoreService;
import com.modlix.saas.worker.feign.IFeignEntityProcessorService;
import com.modlix.saas.worker.feign.IFeignFilesService;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class FilesTtlCleanupServiceTest {

    private IFeignFilesService files;
    private IFeignEntityProcessorService processor;
    private IFeignCoreService core;
    private FilesTtlCleanupService service;

    @BeforeEach
    void setUp() {
        files = mock(IFeignFilesService.class);
        processor = mock(IFeignEntityProcessorService.class);
        core = mock(IFeignCoreService.class);
        service = new FilesTtlCleanupService(files, processor, core);
        ReflectionTestUtils.setField(service, "timeoutMinutes", 1);
        ReflectionTestUtils.setField(service, "maxResultLength", 10_000);

        when(files.cleanupExpired(any(), anyInt())).thenReturn(0);
        when(files.applyRetention(any(), anyInt())).thenReturn(List.of());
        when(processor.stampExpiredWhatsappMedia(anyInt(), anyInt())).thenReturn(0);
        when(core.raiseEvent(any())).thenReturn(true);
    }

    private static FilesRetentionResult group(String client, String app, String type, List<String> paths) {
        return new FilesRetentionResult()
                .setClientCode(client)
                .setAppCode(app)
                .setResourceType(type)
                .setPaths(paths);
    }

    private static List<String> paths(String prefix, int n) {
        return IntStream.range(0, n).mapToObj(i -> prefix + i + ".jpg").toList();
    }

    @Test
    void raisesOneFilesExpiredPerGroupWithItsPaths() {

        when(files.applyRetention(eq("secured"), anyInt()))
                .thenReturn(List.of(
                        group("FIN", "leadzumptwo", "SECURED", paths("/leadzumptwo/whatsapp/incoming/a", 3)),
                        group("ACME", "leadzumptwo", "SECURED", paths("/leadzumptwo/whatsapp/outgoing/b", 2))));

        String result = service.execute(new Task());

        ArgumentCaptor<CoreEventRaiseRequest> captor = ArgumentCaptor.forClass(CoreEventRaiseRequest.class);
        verify(core, times(2)).raiseEvent(captor.capture());

        CoreEventRaiseRequest first = captor.getAllValues().get(0);
        assertEquals("FILES_EXPIRED", first.getEventName());
        assertEquals("leadzumptwo", first.getAppCode());
        assertEquals("FIN", first.getClientCode());
        assertEquals("SECURED", first.getData().get("resourceType"));
        assertEquals(paths("/leadzumptwo/whatsapp/incoming/a", 3), first.getData().get("paths"));

        CoreEventRaiseRequest second = captor.getAllValues().get(1);
        assertEquals("ACME", second.getClientCode());
        assertEquals(paths("/leadzumptwo/whatsapp/outgoing/b", 2), second.getData().get("paths"));

        assertTrue(result.contains("secured retention removed: 5, events raised: 2"), result);
        assertTrue(result.contains("static retention removed: 0, events raised: 0"), result);
    }

    @Test
    void chunksAGroupIntoEventsOfAtMost500Paths() {

        List<String> all = paths("/leadzumptwo/whatsapp/incoming/x", 1201);
        when(files.applyRetention(eq("static"), anyInt()))
                .thenReturn(List.of(group("FIN", "leadzumptwo", "STATIC", all)));

        String result = service.execute(new Task());

        ArgumentCaptor<CoreEventRaiseRequest> captor = ArgumentCaptor.forClass(CoreEventRaiseRequest.class);
        verify(core, times(3)).raiseEvent(captor.capture());

        List<Integer> sizes = captor.getAllValues().stream()
                .map(r -> ((List<?>) r.getData().get("paths")).size())
                .toList();
        assertEquals(List.of(500, 500, 201), sizes);
        assertEquals(all.subList(1000, 1201), captor.getAllValues().get(2).getData().get("paths"));
        assertTrue(result.contains("static retention removed: 1201, events raised: 3"), result);
    }

    @Test
    void aFailedRaiseNeverStopsTheOtherGroupsOrTheStamp() {

        when(files.applyRetention(eq("secured"), anyInt()))
                .thenReturn(List.of(
                        group("FIN", "leadzumptwo", "SECURED", paths("/leadzumptwo/a", 1)),
                        group("ACME", "leadzumptwo", "SECURED", paths("/leadzumptwo/b", 1)),
                        group("XYZ", "otherapp", "SECURED", paths("/otherapp/c", 1))));
        when(core.raiseEvent(any()))
                .thenThrow(new RuntimeException("core down"))
                .thenReturn(false)
                .thenReturn(true);

        String result = service.execute(new Task());

        verify(core, times(3)).raiseEvent(any());
        verify(files).applyRetention(eq("static"), anyInt());
        verify(processor).stampExpiredWhatsappMedia(anyInt(), anyInt());
        assertTrue(result.contains("secured retention removed: 3, events raised: 1, events failed: 2"), result);
    }

    @Test
    void aFailedRetentionCallStillRunsTheOtherBucketAndTheStamp() {

        when(files.applyRetention(eq("secured"), anyInt())).thenThrow(new RuntimeException("files down"));
        when(files.applyRetention(eq("static"), anyInt()))
                .thenReturn(List.of(group("FIN", "leadzumptwo", null, paths("/leadzumptwo/s", 1))));

        String result = service.execute(new Task());

        ArgumentCaptor<CoreEventRaiseRequest> captor = ArgumentCaptor.forClass(CoreEventRaiseRequest.class);
        verify(core).raiseEvent(captor.capture());
        // The bucket fills in for a group that did not name one.
        assertEquals("STATIC", captor.getValue().getData().get("resourceType"));
        verify(processor).stampExpiredWhatsappMedia(anyInt(), anyInt());
        assertTrue(result.contains("secured retention error: files down"), result);
    }

    @Test
    void lifetimesFirstThenRetentionThenTheStamp() {

        service.execute(new Task());

        InOrder order = inOrder(files, processor);
        order.verify(files).cleanupExpired(eq("secured"), anyInt());
        order.verify(files).cleanupExpired(eq("static"), anyInt());
        order.verify(files).applyRetention(eq("secured"), anyInt());
        order.verify(files).applyRetention(eq("static"), anyInt());
        order.verify(processor).stampExpiredWhatsappMedia(anyInt(), anyInt());
        verify(core, never()).raiseEvent(any());
    }

    @Test
    void emptyOrNullGroupsRaiseNothing() {

        when(files.applyRetention(eq("secured"), anyInt())).thenReturn(null);
        when(files.applyRetention(eq("static"), anyInt()))
                .thenReturn(java.util.Arrays.asList(null, group("FIN", "leadzumptwo", "STATIC", List.of())));

        String result = service.execute(new Task());

        verify(core, never()).raiseEvent(any());
        assertTrue(result.contains("secured retention removed: 0"), result);
    }
}
