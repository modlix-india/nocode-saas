package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.message.model.message.whatsapp.graph.FileHandle;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadSessionId;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadStatus;
import com.fincity.saas.message.model.request.message.provider.whatsapp.graph.UploadRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.graph.UploadSessionRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.base.IMessageAccessService;
import com.fincity.saas.message.service.message.MessageConnectionService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.WhatsappApiFactory;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WhatsappUploadService implements IMessageAccessService {

    private static final String KEY_META_APP_ID = "metaAppId";
    private static final String PARAM_UPLOAD_SESSION_ID = "uploadSessionId";
    private static final String PARAM_FILE = "file";

    @Getter
    private MessageResourceService msgService;

    @Getter
    private IFeignSecurityService securityService;

    private MessageConnectionService messageConnectionService;
    private WhatsappApiFactory whatsappApiFactory;

    @Autowired
    private void setMsgService(MessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    private void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    private void setMessageConnectionService(MessageConnectionService messageConnectionService) {
        this.messageConnectionService = messageConnectionService;
    }

    @Autowired
    private void setWhatsappApiFactory(WhatsappApiFactory whatsappApiFactory) {
        this.whatsappApiFactory = whatsappApiFactory;
    }

    protected <T> Mono<T> throwMissingParam(String paramName) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.MISSING_MESSAGE_PARAMETERS,
                ConnectionSubType.WHATSAPP.getProvider(),
                paramName);
    }

    private Mono<Connection> isValidConnection(Connection connection) {

        String facebookAppId = (String) connection.getConnectionDetails().getOrDefault(KEY_META_APP_ID, null);

        if (facebookAppId == null || facebookAppId.isEmpty()) return this.throwMissingParam(KEY_META_APP_ID);

        if (connection.getConnectionType() != ConnectionType.TEXT
                || !connection.getConnectionSubType().equals(ConnectionSubType.WHATSAPP))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.INVALID_CONNECTION_TYPE,
                    connection.getConnectionType(),
                    connection.getConnectionSubType(),
                    "whatsapp upload");

        return Mono.just(connection);
    }

    public Mono<UploadSessionId> startUploadSession(UploadSessionRequest uploadSessionRequest) {

        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.messageConnectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), uploadSessionRequest.getConnectionName()),
                (access, connection) -> this.isValidConnection(connection),
                (access, connection, vConnection) ->
                        whatsappApiFactory.newResumableUploadApiFromConnection(vConnection),
                (access, connection, vConnection, api) -> {
                    String facebookAppId =
                            (String) connection.getConnectionDetails().getOrDefault(KEY_META_APP_ID, null);

                    return api.startUploadSession(
                            facebookAppId,
                            uploadSessionRequest.getFileName(),
                            uploadSessionRequest.getFileLength(),
                            uploadSessionRequest.getFileType());
                });
    }

    public Mono<FileHandle> startOrResumeUpload(UploadRequest uploadRequest, Mono<FilePart> filePartMono) {

        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.messageConnectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), uploadRequest.getConnectionName()),
                (access, connection) -> this.isValidConnection(connection),
                (access, connection, vConnection) ->
                        whatsappApiFactory.newResumableUploadApiFromConnection(vConnection),
                (access, connection, vConnection, api) -> {
                    if (uploadRequest.getUploadSessionId() == null
                            || uploadRequest.getUploadSessionId().isNull())
                        return this.throwMissingParam(PARAM_UPLOAD_SESSION_ID);
                    if (filePartMono == null) return this.throwMissingParam(PARAM_FILE);

                    long offset = uploadRequest.getFileOffset() != null ? uploadRequest.getFileOffset() : 0L;

                    return readFilePartBytes(filePartMono)
                            .flatMap(bytes ->
                                    api.startOrResumeUpload(uploadRequest.getUploadSessionId(), offset, bytes));
                });
    }

    public Mono<UploadStatus> getUploadStatus(UploadRequest uploadRequest) {

        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.messageConnectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), uploadRequest.getConnectionName()),
                (access, connection) -> this.isValidConnection(connection),
                (access, connection, vConnection) ->
                        whatsappApiFactory.newResumableUploadApiFromConnection(vConnection),
                (access, connection, vConnection, api) -> {
                    if (uploadRequest.getUploadSessionId() == null
                            || uploadRequest.getUploadSessionId().isNull())
                        return this.throwMissingParam(PARAM_UPLOAD_SESSION_ID);

                    return api.getUploadStatus(uploadRequest.getUploadSessionId());
                });
    }

    public Mono<FileHandle> resumeUploadFromStatus(UploadRequest uploadRequest, Mono<FilePart> filePartMono) {

        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.messageConnectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), uploadRequest.getConnectionName()),
                (access, connection) -> this.isValidConnection(connection),
                (access, connection, vConnection) ->
                        whatsappApiFactory.newResumableUploadApiFromConnection(vConnection),
                (access, connection, vConnection, api) -> {
                    if (uploadRequest.getUploadSessionId() == null
                            || uploadRequest.getUploadSessionId().isNull())
                        return this.throwMissingParam(PARAM_UPLOAD_SESSION_ID);
                    return readFilePartBytes(filePartMono)
                            .flatMap(bytes -> api.resumeUploadFromStatus(uploadRequest.getUploadSessionId(), bytes));
                });
    }

    private Mono<byte[]> readFilePartBytes(Mono<FilePart> filePartMono) {
        if (filePartMono == null) {
            return this.throwMissingParam(PARAM_FILE);
        }
        return filePartMono
                .switchIfEmpty(this.throwMissingParam(PARAM_FILE))
                .flatMap(fp -> DataBufferUtils.join(fp.content()))
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                });
    }
}
