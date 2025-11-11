package com.modlix.saas.files.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import javax.imageio.ImageIO;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.service.CacheService;
import com.modlix.saas.commons2.util.BooleanUtil;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.files.dao.FileSystemDao;
import com.modlix.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.modlix.saas.files.jooq.enums.FilesFileSystemType;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.model.ImageDetails;
import com.modlix.saas.files.util.ImageTransformUtil;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.services.s3.S3Client;

@Service
public class StaticFileResourceService extends AbstractFilesResourceService {

    private static final String WITH_IN_SUB_CLIENT = "_withInSubClient";
    private static final String ALL_SUB_CLIENTS = "_allSubClients";

    // In static resources the special folders will give access to clients and sub
    // clients to browse.
    private static final Set<String> SPECIAL_FOLDERS = Set.of(WITH_IN_SUB_CLIENT, ALL_SUB_CLIENTS);

    @Value("${files.resources.bucketPrefix:}")
    private String bucketPrefix;

    private FileSystemService fileSystemService;

    private final FileSystemDao fileSystemDao;
    private final CacheService cacheService;
    private final S3Client s3Client;

    private final IFeignSecurityService securityService;

    public StaticFileResourceService(
            FilesAccessPathService filesAccessPathService, FilesMessageResourceService msgService,
            FileSystemDao fileSystemDao, CacheService cacheService, S3Client s3Client,
            FilesUploadDownloadService fileUploadDownloadService, IFeignSecurityService securityService) {
        super(filesAccessPathService, msgService, fileUploadDownloadService);
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
                this.s3Client, FilesFileSystemType.STATIC, this.msgService);
    }

    @Override
    public FileSystemService getFSService() {
        return this.fileSystemService;
    }

    @Override
    public String getResourceType() {
        return FilesAccessPathResourceType.STATIC.name();
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public FileDetail uploadClientImage(FilePart fp, ImageDetails details, ULong clientId) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        String clientCode = null;
        if (clientId == null) {
            clientCode = ca.getClientCode();
        } else if (this.securityService.isBeingManagedById(ca.getUser().getClientId(), clientId.toBigInteger())) {
            clientCode = this.securityService.getClientById(clientId.toBigInteger()).getCode();
        }

        if (StringUtil.safeIsBlank(clientCode)) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), "User Images");
        }
        try {
            Path tempDirectory = Files.createTempDirectory("imageUpload");
            Path file = tempDirectory.resolve(fp.filename());
            fp.transferTo(file).thenReturn(file.toFile());

            var sourceTuple = ImageTransformUtil.makeSourceImage(file.toFile(), fp.filename());

            BufferedImage transformedImage = ImageTransformUtil.transformImage(sourceTuple.getT1(),
                    BufferedImage.TYPE_INT_ARGB, details);

            File finalFile = tempDirectory.resolve(clientId + ".png").toFile();
            ImageIO.write(transformedImage, "png", finalFile);

            FileDetail fd = this.getFSService().createFileFromFile("SYSTEM",
                    "_clientImages", finalFile.getName(), Paths.get(finalFile.getAbsolutePath()), true);

            return this.convertToFileDetailWhileCreation("/_clientImages", "SYSTEM", fd);
        } catch (IOException ex) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    FilesMessageResourceService.UNKNOWN_ERROR, this.getResourceType(), ex);
        }
    }

    @Override
    protected boolean subClientListAccess(String resourcePath, String clientCode) {

        resourcePath = resourcePath.trim();
        if (resourcePath.isBlank())
            return false;

        if (resourcePath.startsWith("/"))
            resourcePath = resourcePath.substring(1);
        int index = resourcePath.indexOf('/', 1);
        String firstFolderName = resourcePath.substring(resourcePath.startsWith("/") ? 1 : 0,
                index == -1 ? resourcePath.length() : index);

        if (!SPECIAL_FOLDERS.contains(firstFolderName))
            return false;

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        return switch (firstFolderName) {
            case WITH_IN_SUB_CLIENT -> ca.getClientCode().equals(clientCode) ? false
                    : BooleanUtil.safeValueOf(this.securityService.isBeingManaged(clientCode, ca.getClientCode()));
            case ALL_SUB_CLIENTS -> this.securityService.isBeingManaged(clientCode, ca.getClientCode());
            default -> false;
        };

    }
}
