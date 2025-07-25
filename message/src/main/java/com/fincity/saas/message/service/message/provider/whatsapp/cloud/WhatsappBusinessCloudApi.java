package com.fincity.saas.message.service.message.provider.whatsapp.cloud;

import com.fincity.saas.message.configuration.message.whatsapp.ApiVersion;
import com.fincity.saas.message.configuration.message.whatsapp.WhatsappApiConfig;
import com.fincity.saas.message.model.message.whatsapp.media.FileType;
import com.fincity.saas.message.model.message.whatsapp.media.Media;
import com.fincity.saas.message.model.message.whatsapp.media.MediaFile;
import com.fincity.saas.message.model.message.whatsapp.media.UploadResponse;
import com.fincity.saas.message.model.message.whatsapp.messages.Message;
import com.fincity.saas.message.model.message.whatsapp.messages.ReadMessage;
import com.fincity.saas.message.model.message.whatsapp.messages.response.MessageResponse;
import com.fincity.saas.message.model.message.whatsapp.phone.TwoStepCode;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class WhatsappBusinessCloudApi {

    private final WhatsappBusinessCloudApiService apiService;
    private final ApiVersion apiVersion;
    private final WebClient webClient;

    public WhatsappBusinessCloudApi(WebClient webClient) {
        this.apiVersion = WhatsappApiConfig.API_VERSION;
        this.webClient = webClient;
        this.apiService = new WhatsappBusinessCloudApiServiceImpl(webClient);
    }

    public WhatsappBusinessCloudApi(WebClient webClient, ApiVersion apiVersion) {
        this.apiVersion = apiVersion;
        this.webClient = webClient;
        this.apiService = new WhatsappBusinessCloudApiServiceImpl(webClient);
    }

    public Mono<MessageResponse> sendMessage(String phoneNumberId, Message message) {
        return apiService.sendMessage(apiVersion.getValue(), phoneNumberId, message);
    }

    public Mono<UploadResponse> uploadMedia(String phoneNumberId, String fileName, FileType fileType, byte[] file) {
        return apiService.uploadMedia(apiVersion.getValue(), phoneNumberId, fileName, fileType.getType(), file);
    }

    public Mono<Media> retrieveMediaUrl(String mediaId) {
        return apiService.retrieveMediaUrl(apiVersion.getValue(), mediaId);
    }

    public Mono<MediaFile> downloadMediaFile(String url) {
        return apiService.downloadMediaFile(url);
    }

    public Mono<Response> deleteMedia(String mediaId) {
        return apiService.deleteMedia(apiVersion.getValue(), mediaId);
    }

    public Mono<Response> markMessageAsRead(String phoneNumberId, ReadMessage message) {
        return apiService.markMessageAsRead(apiVersion.getValue(), phoneNumberId, message);
    }

    public Mono<Response> twoStepVerification(String phoneNumberId, TwoStepCode twoStepCode) {
        return apiService.twoStepVerification(apiVersion.getValue(), phoneNumberId, twoStepCode);
    }

    private record WhatsappBusinessCloudApiServiceImpl(WebClient webClient) implements WhatsappBusinessCloudApiService {

        @Override
        public Mono<MessageResponse> sendMessage(String apiVersion, String phoneNumberId, Message message) {
            return webClient
                    .post()
                    .uri("/{api-version}/{Phone-Number-ID}/messages", apiVersion, phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(message)
                    .retrieve()
                    .bodyToMono(MessageResponse.class);
        }

        @Override
        public Mono<UploadResponse> uploadMedia(
                String apiVersion, String phoneNumberId, String fileName, String fileType, byte[] fileContent) {
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder
                    .part("file", new ByteArrayResource(fileContent) {
                        @Override
                        public String getFilename() {
                            return fileName;
                        }
                    })
                    .header("Content-Type", fileType);
            bodyBuilder.part("messaging_product", "whatsapp");

            return webClient
                    .post()
                    .uri("/{api-version}/{Phone-Number-ID}/media", apiVersion, phoneNumberId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(UploadResponse.class);
        }

        @Override
        public Mono<Media> retrieveMediaUrl(String apiVersion, String mediaId) {
            return webClient
                    .get()
                    .uri("/{api-version}/{media-id}", apiVersion, mediaId)
                    .retrieve()
                    .bodyToMono(Media.class);
        }

        @Override
        public Mono<MediaFile> downloadMediaFile(String url) {
            return webClient
                    .get()
                    .uri(url)
                    .header("User-Agent", "curl/7.64.1")
                    .retrieve()
                    .toEntity(byte[].class)
                    .map(response -> {
                        String contentDisposition = response.getHeaders().getFirst("Content-Disposition");
                        String fileName =
                                contentDisposition != null ? contentDisposition.split("=")[1] : "unknown_file";
                        byte[] bytes = response.getBody();
                        return new MediaFile().setFileName(fileName).setContent(bytes);
                    });
        }

        @Override
        public Mono<Response> deleteMedia(String apiVersion, String mediaId) {
            return webClient
                    .delete()
                    .uri("/{api-version}/{media-id}", apiVersion, mediaId)
                    .retrieve()
                    .bodyToMono(Response.class);
        }

        @Override
        public Mono<Response> markMessageAsRead(String apiVersion, String phoneNumberId, ReadMessage message) {
            return webClient
                    .post()
                    .uri("/{api-version}/{Phone-Number-ID}/messages", apiVersion, phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(message)
                    .retrieve()
                    .bodyToMono(Response.class);
        }

        @Override
        public Mono<Response> twoStepVerification(String apiVersion, String phoneNumberId, TwoStepCode twoStepCode) {
            return webClient
                    .post()
                    .uri("/{api-version}/{Phone-Number-ID}", apiVersion, phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(twoStepCode)
                    .retrieve()
                    .bodyToMono(Response.class);
        }
    }
}
