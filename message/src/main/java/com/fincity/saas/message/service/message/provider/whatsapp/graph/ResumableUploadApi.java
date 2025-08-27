package com.fincity.saas.message.service.message.provider.whatsapp.graph;

import com.fincity.saas.message.configuration.message.whatsapp.ApiVersion;
import com.fincity.saas.message.model.message.whatsapp.graph.BaseId;
import com.fincity.saas.message.model.message.whatsapp.graph.FileHandle;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadSessionId;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadStatus;
import com.fincity.saas.message.model.message.whatsapp.media.FileType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.AbstractWhatsappApi;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class ResumableUploadApi extends AbstractWhatsappApi {

    private final ResumableUploadApiService apiService;

    public ResumableUploadApi(WebClient webClient, MessageResourceService messageResourceService) {
        super(webClient, messageResourceService);
        this.apiService = (ResumableUploadApiService) createApiService();
    }

    public ResumableUploadApi(
            WebClient webClient, ApiVersion apiVersion, MessageResourceService messageResourceService) {
        super(webClient, apiVersion, messageResourceService);
        this.apiService = (ResumableUploadApiService) createApiService();
    }

    @Override
    protected Object createApiService() {
        return new ResumableUploadApiServiceImpl(webClient, messageResourceService);
    }

    public Mono<UploadSessionId> startUploadSession(String appId, String fileName, long fileLength, FileType fileType) {
        return this.apiService.startUploadSession(
                apiVersion.getValue(), appId, fileName, fileLength, fileType.getType());
    }

    public Mono<FileHandle> startOrResumeUpload(UploadSessionId uploadSessionId, long fileOffset, byte[] fileContent) {
        return this.apiService.startOrResumeUpload(apiVersion.getValue(), uploadSessionId, fileOffset, fileContent);
    }

    public Mono<UploadStatus> getUploadStatus(UploadSessionId uploadSessionId) {
        return this.apiService.getUploadStatus(apiVersion.getValue(), uploadSessionId);
    }

    public Mono<FileHandle> resumeUploadFromStatus(UploadSessionId uploadSessionId, byte[] fileContent) {
        return this.apiService.resumeUploadFromStatus(apiVersion.getValue(), uploadSessionId, fileContent);
    }

    private record ResumableUploadApiServiceImpl(WebClient webClient, MessageResourceService msgService)
            implements ResumableUploadApiService {

        private Mono<Throwable> handleWhatsappApiError(ClientResponse clientResponse) {
            return AbstractWhatsappApi.handleWhatsappApiError(clientResponse, this.msgService);
        }

        @Override
        public Mono<UploadSessionId> startUploadSession(
                String apiVersion, String appId, String fileName, long fileLength, String fileType) {
            return webClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{api-version}/{app-id}/uploads")
                            .queryParam("file_name", fileName)
                            .queryParam("file_length", fileLength)
                            .queryParam("file_type", fileType)
                            .build(apiVersion, appId))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(UploadSessionId.class);
        }

        @Override
        public Mono<FileHandle> startOrResumeUpload(
                String apiVersion, UploadSessionId uploadSessionId, long fileOffset, byte[] fileContent) {

            return webClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{api-version}/upload:{uploadSessionId}")
                            .queryParam("sig", uploadSessionId.getSig())
                            .build(apiVersion, uploadSessionId.getUpload()))
                    .header("file_offset", String.valueOf(fileOffset))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .bodyValue(fileContent)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(FileHandle.class);
        }

        @Override
        public Mono<UploadStatus> getUploadStatus(String apiVersion, UploadSessionId uploadSessionId) {

            return webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{api-version}/upload:{uploadSessionId}")
                            .queryParam("sig", uploadSessionId.getSig())
                            .build(apiVersion, uploadSessionId.getUpload()))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(UploadStatus.class);
        }

        @Override
        public Mono<FileHandle> resumeUploadFromStatus(
                String apiVersion, UploadSessionId uploadSessionId, byte[] fileContent) {
            return this.getUploadStatus(apiVersion, uploadSessionId)
                    .map(status -> status != null && status.getFileOffset() != null ? status.getFileOffset() : 0L)
                    .flatMap(offset -> this.startOrResumeUpload(apiVersion, uploadSessionId, offset, fileContent));
        }
    }
}
