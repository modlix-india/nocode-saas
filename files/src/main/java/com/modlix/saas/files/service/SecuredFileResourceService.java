package com.modlix.saas.files.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.jooq.util.ULongUtil;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.service.CacheService;
import com.modlix.saas.commons2.util.BooleanUtil;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.commons2.util.Tuples;
import com.modlix.saas.commons2.util.UniqueUtil;
import com.modlix.saas.files.dao.FileSystemDao;
import com.modlix.saas.files.dto.FilesSecuredAccessKey;
import com.modlix.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.modlix.saas.files.jooq.enums.FilesFileSystemType;
import com.modlix.saas.files.model.DownloadOptions;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.model.ImageDetails;
import com.modlix.saas.files.util.ImageTransformUtil;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;

@Service
public class SecuredFileResourceService extends AbstractFilesResourceService {

    private static final String CREATE_KEY = "/createKey";

    private static final String USER_IMAGES = "_userImages";
    public static final String WITH_IN_CLIENT = "_withInClient";
    private static final String WITH_IN_SUB_CLIENT = "_withInSubClient";
    private static final String ALL_SUB_CLIENTS = "_allSubClients";

    private static final Set<String> SPECIAL_FOLDERS = Set.of(
            WITH_IN_CLIENT, WITH_IN_SUB_CLIENT, ALL_SUB_CLIENTS);

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
    private final S3Client s3Client;
    private final S3AsyncClient s3AsyncClient;
    private final IFeignSecurityService securityService;

    public SecuredFileResourceService(FilesSecuredAccessService fileSecuredAccessService,
            FilesAccessPathService filesAccessPathService, FilesMessageResourceService msgService,
            FileSystemDao fileSystemDao, CacheService cacheService, S3Client s3Client,
            S3AsyncClient s3AsyncClient,
            FilesUploadDownloadService fileUploadDownloadService, IFeignSecurityService securityService,
            FilesMessageResourceService messageService) {
        super(filesAccessPathService, msgService, fileUploadDownloadService);
        this.fileSecuredAccessService = fileSecuredAccessService;
        this.fileSystemDao = fileSystemDao;
        this.cacheService = cacheService;
        this.s3Client = s3Client;
        this.s3AsyncClient = s3AsyncClient;
        this.securityService = securityService;
    }

    @Override
    @PostConstruct
    public void initialize() {
        super.initialize();
        String bucketName = this.bucketPrefix + "-" + this.getResourceType().toLowerCase();

        this.fileSystemService = new FileSystemService(this.fileSystemDao, this.cacheService, bucketName,
                this.s3Client, this.s3AsyncClient, FilesFileSystemType.SECURED, this.msgService);
    }

    @Override
    protected boolean checkReadAccessWithClientCode(String resourcePath) {

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
        String firstFolderName = (index != -1) ? resourcePath.substring(resourcePath.startsWith("/") ? 1 : 0, index)
                : null;

        if (firstFolderName != null && (SPECIAL_FOLDERS.contains(firstFolderName) ||
                (firstFolderName.equals(USER_IMAGES) && finalClientCode.equals("SYSTEM")))) {

            ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

            return switch (firstFolderName) {
                case USER_IMAGES -> true;
                case WITH_IN_CLIENT -> ca.getClientCode().equals(finalClientCode);
                case WITH_IN_SUB_CLIENT -> ca.getClientCode().equals(finalClientCode) ? false
                        : BooleanUtil
                                .safeValueOf(this.securityService.isBeingManaged(finalClientCode, ca.getClientCode()));
                case ALL_SUB_CLIENTS ->
                    BooleanUtil.safeValueOf(this.securityService.isBeingManaged(finalClientCode, ca.getClientCode()));
                default -> false;
            };
        }
        return this.fileAccessService.hasReadAccess(resourcePath, finalClientCode, FilesAccessPathResourceType.SECURED);
    }

    @Override
    public FileDetail create(String clientCode, String uri, List<MultipartFile> fp, String fileName, Boolean override) {

        if (override == null)
            override = false;

        return super.create(clientCode, uri, fp, fileName, override);
    }

    public String createSecuredAccess(Long timeSpan, ChronoUnit timeUnit, Long accessLimit, String uri) {

        String path = uri.replace(CREATE_KEY, "");

        Tuples.Tuple2<String, String> tup = super.resolvePathWithClientCode(path);

        boolean hasReadability = this.checkReadAccessWithClientCode(tup.getT2());

        if (!hasReadability) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), tup.getT2());
        }

        String accessKey = this.createAccessKey(timeSpan, timeUnit, accessLimit, tup.getT2());

        return this.secureAccessPathUri + accessKey;
    }

    public void downloadFileByKey(String key, DownloadOptions downloadOptions, HttpServletRequest request,
            HttpServletResponse response) {

        if (StringUtil.safeIsBlank(key)) {
            return;
        }

        String accessPath = this.fileSecuredAccessService.getAccessPathByKey(key);

        if (StringUtil.safeIsBlank(accessPath))
            return;
        FileDetail fileDetail = this.getFSService().getFileDetail(accessPath);

        this.getFSService().getAsFile(accessPath, downloadOptions.getDownload());

        String fileETag = generateFileETag(fileDetail, fileDetail.getLastModifiedTime(), downloadOptions);

        super.makeMatchesStartDownload(downloadOptions, request, response,
                accessPath, fileDetail.getLastModifiedTime(),
                fileETag);
    }

    private String createAccessKey(Long time, ChronoUnit unit, Long limit, String path) {

        if (unit == null && time != null)
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    FilesMessageResourceService.TIME_UNIT_ERROR);

        if (time == null && limit != null)
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    FilesMessageResourceService.TIME_SPAN_ERROR);

        time = time == null || time.toString().isBlank() ? defaultAccessTimeLimit : time;
        unit = unit == null ? defaultChronoUnit : unit;
        int pathIndex = path.indexOf('?');
        path = pathIndex != -1 ? path.substring(0, pathIndex) : path;

        FilesSecuredAccessKey fileSecuredAccessKey = new FilesSecuredAccessKey().setPath(path)
                .setAccessKey(UniqueUtil.base36UUID())
                .setAccessLimit(ULongUtil.valueOf(limit))
                .setAccessTill(LocalDateTime.now()
                        .plus(time, unit));

        return fileSecuredAccessService.create(fileSecuredAccessKey).getAccessKey();
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

    public FileDetail uploadUserImage(MultipartFile fp, ImageDetails details, ULong userId) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        ULong uid;

        if (userId == null) {
            uid = ULong.valueOf(ca.getUser().getId());
        } else {
            uid = BooleanUtil
                    .safeValueOf(this.securityService.isUserBeingManaged(userId.toBigInteger(), ca.getClientCode()))
                    && SecurityContextUtil.hasAuthority("Authorities.User_UPDATE",
                            ca.getUser().getAuthorities()) ? ULong.valueOf(userId.toBigInteger()) : null;
        }
        try {
            Path tempDirectory = Files.createTempDirectory("imageUpload");
            Path file = tempDirectory.resolve(fp.getOriginalFilename());
            fp.transferTo(file);

            Tuples.Tuple2<BufferedImage, Integer> sourceTuple = ImageTransformUtil.makeSourceImage(file.toFile(),
                    fp.getOriginalFilename());
            BufferedImage transformedImage = ImageTransformUtil.transformImage(sourceTuple.getT1(),
                    BufferedImage.TYPE_INT_ARGB, details);
            File finalFile = tempDirectory.resolve(uid + ".png").toFile();
            ImageIO.write(transformedImage, "png", finalFile);

            FileDetail fileDetail = this.getFSService().createFileFromFile("SYSTEM",
                    USER_IMAGES, finalFile.getName(), Paths.get(finalFile.getAbsolutePath()), true);
            return this.convertToFileDetailWhileCreation("/_userImages", "SYSTEM", fileDetail);
        } catch (IOException e) {
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    FilesMessageResourceService.UNKNOWN_ERROR, e.getMessage(), e);
            return null;
        }
    }
}
