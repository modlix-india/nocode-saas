package com.fincity.saas.message.service.message.provider.whatsapp.cloud;

import com.fincity.saas.message.model.message.whatsapp.media.Media;
import com.fincity.saas.message.model.message.whatsapp.media.MediaFile;
import com.fincity.saas.message.model.message.whatsapp.media.UploadResponse;
import com.fincity.saas.message.model.message.whatsapp.messages.Message;
import com.fincity.saas.message.model.message.whatsapp.messages.ReadMessage;
import com.fincity.saas.message.model.message.whatsapp.messages.response.MessageResponse;
import com.fincity.saas.message.model.message.whatsapp.phone.TwoStepCode;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import reactor.core.publisher.Mono;

public interface WhatsappBusinessCloudApiService {

    Mono<MessageResponse> sendMessage(String apiVersion, String phoneNumberId, Message message);

    Mono<UploadResponse> uploadMedia(
            String apiVersion, String phoneNumberId, String fileName, String fileType, byte[] fileContent);

    Mono<Media> retrieveMediaUrl(String apiVersion, String mediaId);

    Mono<MediaFile> downloadMediaFile(String url);

    Mono<Response> deleteMedia(String apiVersion, String mediaId);

    Mono<Response> markMessageAsRead(String apiVersion, String phoneNumberId, ReadMessage message);

    Mono<Response> twoStepVerification(String apiVersion, String phoneNumberId, TwoStepCode twoStepCode);
}
