package com.fincity.saas.message.service.message.provider.whatsapp.graph;

import com.fincity.saas.message.model.message.whatsapp.graph.BaseId;
import com.fincity.saas.message.model.message.whatsapp.graph.FileHandle;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadStatus;
import reactor.core.publisher.Mono;

public interface ResumableUploadApiService {

    Mono<BaseId> startUploadSession(String apiVersion, String appId, String fileName, long fileLength, String fileType);

    Mono<FileHandle> startOrResumeUpload(
            String apiVersion, String uploadSessionId, long fileOffset, byte[] fileContent);

    Mono<UploadStatus> getUploadStatus(String apiVersion, String uploadSessionId);

    Mono<FileHandle> resumeUploadFromStatus(String apiVersion, String uploadSessionId, byte[] fileContent);
}
