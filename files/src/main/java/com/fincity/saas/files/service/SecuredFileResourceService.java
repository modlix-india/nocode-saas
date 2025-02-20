package com.fincity.saas.files.service;

import static com.fincity.saas.commons.util.StringUtil.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.files.model.ImageDetails;
import com.fincity.saas.files.util.ImageTransformUtil;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.files.dao.FileSystemDao;
import com.fincity.saas.files.dto.FilesSecuredAccessKey;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.jooq.enums.FilesFileSystemType;
import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.model.FileDetail;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import javax.imageio.ImageIO;

@Service
public class SecuredFileResourceService extends AbstractFilesResourceService {

    private static final String CREATE_KEY = "/createKey";

    private static final String USER_IMAGES = "_userImages";
    private static final String WITH_IN_CLIENT = "_withInClient";
    private static final String WITH_IN_SUB_CLIENT = "_withInSubClient";
    private static final String ALL_SUB_CLIENTS = "_allSubClients";

    private static final Set<String> SPECIAL_FOLDERS = Set.of(
        WITH_IN_CLIENT, WITH_IN_SUB_CLIENT, ALL_SUB_CLIENTS
    );

    @Value("${files.timeLimit:365}")
    private Long defaultAccessTimeLimit;

    @Value("${files.timeUnit:DAYS}")
    private ChronoUnit defaultChronoUnit;

    @Value("${files.secureKeyURI:api/files/secured/downloadFileByKey/}")
    private String secureAccessPathUri;

    @Value("${files.resources.bucketPrefix:}")
    private String bucketPrefix;

    private FileSystemService fileSystemService;

    private final FilesSecuredAccessService fileSecuredAccessService;

    private final FileSystemDao fileSystemDao;
    private final CacheService cacheService;
    private final S3AsyncClient s3Client;

    private final IFeignSecurityService securityService;

    public SecuredFileResourceService(FilesSecuredAccessService fileSecuredAccessService,
                                      FilesAccessPathService filesAccessPathService, FilesMessageResourceService msgService,
                                      FileSystemDao fileSystemDao, CacheService cacheService, S3AsyncClient s3Client,
                                      FilesUploadDownloadService fileUploadDownloadService, IFeignSecurityService securityService) {
        super(filesAccessPathService, msgService, fileUploadDownloadService);
        this.fileSecuredAccessService = fileSecuredAccessService;
        this.fileSystemDao = fileSystemDao;
        this.cacheService = cacheService;
        this.s3Client = s3Client;
        this.securityService = securityService;
    }

    @Override
    @PostConstruct
    public void initialize() {
        super.initialize();
        String bucketName = this.bucketPrefix + "-" + this.getResourceType().toLowerCase();

        this.fileSystemService = new FileSystemService(this.fileSystemDao, this.cacheService, bucketName,
            this.s3Client, FilesFileSystemType.SECURED);
    }

    @Override
    protected Mono<Boolean> checkReadAccessWithClientCode(String resourcePath) {

        int index = resourcePath.indexOf('/', 1);
        String clientCode;
        if (index != -1) {

            clientCode = resourcePath.substring(0, index);
            resourcePath = resourcePath.substring(index);
        } else {

            clientCode = resourcePath;
            resourcePath = "";
        }

        String finalClientCode = clientCode;
        index = resourcePath.indexOf('/', index + 1);
        String firstFolderName = (index != -1) ? resourcePath.substring(resourcePath.startsWith("/") ? 1 : 0, index) : null;

        if (firstFolderName != null && (SPECIAL_FOLDERS.contains(firstFolderName) ||
            (firstFolderName.equals(USER_IMAGES) && finalClientCode.equals("SYSTEM"))))
            return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> switch (firstFolderName) {
                    case USER_IMAGES -> Mono.just(true);
                    case WITH_IN_CLIENT -> Mono.just(ca.getClientCode().equals(finalClientCode));
                    case WITH_IN_SUB_CLIENT ->
                        ca.getClientCode().equals(finalClientCode) ? Mono.just(false) : this.securityService.isBeingManaged(finalClientCode, ca.getClientCode());
                    case ALL_SUB_CLIENTS -> this.securityService.isBeingManaged(finalClientCode, ca.getClientCode());
                    default -> Mono.just(false);
                }
            ).contextWrite(Context.of(LogUtil.METHOD_NAME, "SecuredFileResourceService.checkReadAccessWithClientCode"));

        return this.fileAccessService.hasReadAccess(resourcePath, finalClientCode, FilesAccessPathResourceType.SECURED);
    }

    @Override
    public Mono<Object> create(String clientCode, String uri, Flux<FilePart> fp, String fileName, Boolean override) {

        if (override == null)
            override = false;

        return super.create(clientCode, uri, fp, fileName, override);
    }

    public Mono<String> createSecuredAccess(Long timeSpan, ChronoUnit timeUnit, Long accessLimit, String uri) {

        String path = uri.replace(CREATE_KEY, "");

        Tuple2<String, String> tup = super.resolvePathWithClientCode(path);

        return FlatMapUtil.flatMapMono(

                () -> this.checkReadAccessWithClientCode(tup.getT2())
                    .flatMap(BooleanUtil::safeValueOfWithEmpty),

                hasReadability -> this.createAccessKey(timeSpan, timeUnit, accessLimit, tup.getT2()),

                (hasReadability, accessKey) -> Mono.just(this.secureAccessPathUri + accessKey)

            )
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SecuredFileResourceService.createSecuredAccess"))
            .switchIfEmpty(this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                FilesMessageResourceService.SECURED_KEY_CREATION_ERROR));
    }

    public Mono<Void> downloadFileByKey(String key, DownloadOptions downloadOptions, ServerHttpRequest request,
                                        ServerHttpResponse response) {

        if (safeIsBlank(key)) {
            return null;
        }

        return FlatMapUtil.flatMapMono(

            () -> this.fileSecuredAccessService.getAccessPathByKey(key),

            accessPath -> {
                if (safeIsBlank(accessPath)) {
                    return Mono.empty();
                }

                return this.getFSService().getFileDetail(accessPath);
            },

            (accessPath, fileDetail) -> this.getFSService().getAsFile(accessPath, downloadOptions.getDownload()),

            (accessPath, fileDetail, file) -> {

                String fileETag = generateFileETag(fileDetail, fileDetail.getLastModifiedTime(), downloadOptions);
                return super.makeMatchesStartDownload(downloadOptions, request, response,
                    accessPath, fileDetail.getLastModifiedTime(),
                    fileETag);
            }).contextWrite(Context.of(LogUtil.METHOD_NAME, "SecuredFileResourceService.downloadFileByKey"));
    }

    private Mono<String> createAccessKey(Long time, ChronoUnit unit, Long limit, String path) {

        if (unit == null && time != null)
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                FilesMessageResourceService.TIME_UNIT_ERROR);

        if (time == null && limit != null)
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                FilesMessageResourceService.TIME_SPAN_ERROR);

        time = time == null || time.toString().isBlank() ? defaultAccessTimeLimit : time;
        unit = safeIsBlank(unit) ? defaultChronoUnit : unit;
        int pathIndex = path.indexOf('?');
        path = pathIndex != -1 ? path.substring(0, pathIndex) : path;

        FilesSecuredAccessKey fileSecuredAccessKey = new FilesSecuredAccessKey().setPath(path)
            .setAccessKey(UniqueUtil.base36UUID())
            .setAccessLimit(ULongUtil.valueOf(limit))
            .setAccessTill(LocalDateTime.now()
                .plus(time, unit));

        return fileSecuredAccessService.create(fileSecuredAccessKey).map(FilesSecuredAccessKey::getAccessKey);
    }

    private String generateFileETag(FileDetail fileDetail, long fileMillis, DownloadOptions downloadOptions) {
        return String.format("\"%d-%d-%s\"", fileDetail.getName().hashCode(), fileMillis, downloadOptions.eTagCode());
    }

    @Override
    public FileSystemService getFSService() {
        return this.fileSystemService;
    }

    @Override
    public String getResourceType() {
        return FilesAccessPathResourceType.SECURED.name();
    }

    public Mono<FileDetail> uploadUserImage(FilePart fp, ImageDetails details, ULong userId) {

        return FlatMapUtil.flatMapMono(

            SecurityContextUtil::getUsersContextAuthentication,

            ca -> (userId == null) ? Mono.just(ca.getUser().getId()) :
                this.securityService.isUserBeingManaged(userId.toBigInteger(), ca.getClientCode())
                    .filter(Boolean::booleanValue)
                    .filter(t -> SecurityContextUtil.hasAuthority("Authorities.User_UPDATE", ca.getUser().getAuthorities()))
                    .map(e -> userId.toBigInteger()),

            (ca, uid) ->
                Mono.fromCallable(() -> Files.createTempDirectory("imageUpload"))
                    .subscribeOn(Schedulers.boundedElastic()),

            (ca, uid, tempDirectory) -> {

                Path file = tempDirectory.resolve(fp.filename());
                return fp.transferTo(file).thenReturn(file.toFile());
            },

            (ca, uid, tempDirectory, file) ->
                Mono.fromCallable(() -> ImageTransformUtil.makeSourceImage(file, fp.filename())),

            (ca, uid, temp, file, sourceTuple) ->
                Mono.defer(() -> Mono.just(Tuples.of(
                        ImageTransformUtil.transformImage(sourceTuple.getT1(), BufferedImage.TYPE_INT_ARGB, details),
                        BufferedImage.TYPE_INT_ARGB)))
                    .subscribeOn(Schedulers.boundedElastic()),

            (ca, uid, temp, file,
             sTuple, imgTuple) ->
                Mono.fromCallable(() -> {
                    File finalFile = temp.resolve(uid + ".png").toFile();
                    ImageIO.write(imgTuple.getT1(), "png", finalFile);
                    return finalFile;
                }).subscribeOn(Schedulers.boundedElastic()),

            (ca, uid, temp, file,
             sTuple, imgTuple, finalFile) ->
                this.getFSService().createFileFromFile("SYSTEM",
                        "_userImages", finalFile.getName(), Paths.get(finalFile.getAbsolutePath()), true)
                    .map(fd -> this.convertToFileDetailWhileCreation("/_userImages", "SYSTEM", fd))
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "SecuredFileResourceService.uploadUserImage"));
    }
}
