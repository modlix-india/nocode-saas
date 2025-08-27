package com.fincity.saas.message.service.message.provider.whatsapp.graph;

import com.fincity.saas.message.configuration.message.whatsapp.ApiVersion;
import com.fincity.saas.message.model.message.whatsapp.graph.BaseId;
import com.fincity.saas.message.model.message.whatsapp.graph.FileHandle;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadStatus;
import com.fincity.saas.message.model.message.whatsapp.media.FileType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.AbstractWhatsappApi;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class ResumableUploadApi extends AbstractWhatsappApi {

    private static final String UPLOAD_ID_PREFIX = "upload:";
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

    public Mono<BaseId> startUploadSession(String appId, String fileName, long fileLength, FileType fileType) {
        return this.apiService.startUploadSession(
                apiVersion.getValue(), appId, fileName, fileLength, fileType.getType());
    }

    public Mono<FileHandle> startOrResumeUpload(String uploadSessionId, long fileOffset, byte[] fileContent) {
        return this.apiService.startOrResumeUpload(apiVersion.getValue(), uploadSessionId, fileOffset, fileContent);
    }

    public Mono<UploadStatus> getUploadStatus(String uploadSessionId) {
        return this.apiService.getUploadStatus(apiVersion.getValue(), uploadSessionId);
    }

    public Mono<FileHandle> resumeUploadFromStatus(String uploadSessionId, byte[] fileContent) {
        return this.apiService.resumeUploadFromStatus(apiVersion.getValue(), uploadSessionId, fileContent);
    }

    private record ResumableUploadApiServiceImpl(WebClient webClient, MessageResourceService msgService)
            implements ResumableUploadApiService {

        private Mono<Throwable> handleWhatsappApiError(ClientResponse clientResponse) {
            return AbstractWhatsappApi.handleWhatsappApiError(clientResponse, this.msgService);
        }

        @Override
        public Mono<BaseId> startUploadSession(
                String apiVersion, String appId, String fileName, long fileLength, String fileType) {
            return webClient
                    .post()
                    .uri(
                            "/{api-version}/{app-id}/uploads?file_name={fileName}&file_length={fileLength}&file_type={fileType}",
                            apiVersion,
                            appId,
                            fileName,
                            fileLength,
                            fileType)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(BaseId.class);
        }

        @Override
        public Mono<FileHandle> startOrResumeUpload(
                String apiVersion, String uploadSessionId, long fileOffset, byte[] fileContent) {

            if (!uploadSessionId.startsWith(UPLOAD_ID_PREFIX)) uploadSessionId = UPLOAD_ID_PREFIX + uploadSessionId;

            return webClient
                    .post()
                    .uri("/{api-version}/{uploadSessionId}", apiVersion, uploadSessionId)
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
        public Mono<UploadStatus> getUploadStatus(String apiVersion, String uploadSessionId) {

            if (!uploadSessionId.startsWith(UPLOAD_ID_PREFIX)) uploadSessionId = UPLOAD_ID_PREFIX + uploadSessionId;

            return webClient
                    .get()
                    .uri("/{api-version}/{uploadSessionId}", apiVersion, uploadSessionId)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(UploadStatus.class);
        }

        @Override
        public Mono<FileHandle> resumeUploadFromStatus(String apiVersion, String uploadSessionId, byte[] fileContent) {
            return this.getUploadStatus(apiVersion, uploadSessionId)
                    .map(status -> status != null && status.getFileOffset() != null ? status.getFileOffset() : 0L)
                    .flatMap(offset -> this.startOrResumeUpload(apiVersion, uploadSessionId, offset, fileContent));
        }
    }
}
