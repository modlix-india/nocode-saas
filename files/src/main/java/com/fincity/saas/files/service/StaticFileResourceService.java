package com.fincity.saas.files.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.model.ImageDetails;
import com.fincity.saas.files.util.ImageTransformUtil;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.files.dao.FileSystemDao;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.jooq.enums.FilesFileSystemType;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuples;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Service
public class StaticFileResourceService extends AbstractFilesResourceService {

    private static final String WITH_IN_SUB_CLIENT = "_withInSubClient";
    private static final String ALL_SUB_CLIENTS = "_allSubClients";

    //In static resources the special folders will give access to clients and sub clients to browse.
    private static final Set<String> SPECIAL_FOLDERS = Set.of(WITH_IN_SUB_CLIENT, ALL_SUB_CLIENTS);

    @Value("${files.resources.bucketPrefix:}")
    private String bucketPrefix;

    private FileSystemService fileSystemService;

    private final FileSystemDao fileSystemDao;
    private final CacheService cacheService;
    private final S3AsyncClient s3Client;

    private final IFeignSecurityService securityService;

    public StaticFileResourceService(
            FilesAccessPathService filesAccessPathService, FilesMessageResourceService msgService,
            FileSystemDao fileSystemDao, CacheService cacheService, S3AsyncClient s3Client,
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
                this.s3Client, FilesFileSystemType.STATIC);
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
    public Mono<FileDetail> uploadClientImage(FilePart fp, ImageDetails details, ULong clientId) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> (clientId == null) ? Mono.just(ca.getClientCode())
                                : this.securityService.isBeingManagedById(ca.getUser().getClientId(), clientId.toBigInteger())
                                .filter(Boolean::booleanValue).map(e -> clientId.toBigInteger())
                                .flatMap(this.securityService::getClientById).map(Client::getCode),

                        (ca, cid) -> Mono.fromCallable(() -> Files.createTempDirectory("imageUpload"))
                                .subscribeOn(Schedulers.boundedElastic()),

                        (ca, cid, tempDirectory) -> {

                            Path file = tempDirectory.resolve(fp.filename());
                            return fp.transferTo(file).thenReturn(file.toFile());
                        },

                        (ca, cid, tempDirectory, file) -> Mono
                                .fromCallable(() -> ImageTransformUtil.makeSourceImage(file, fp.filename())),

                        (ca, cid, temp, file, sourceTuple) -> Mono.defer(() -> Mono.just(Tuples.of(
                                        ImageTransformUtil.transformImage(sourceTuple.getT1(), BufferedImage.TYPE_INT_ARGB, details),
                                        BufferedImage.TYPE_INT_ARGB)))
                                .subscribeOn(Schedulers.boundedElastic()),

                        (ca, cid, temp, file, sTuple, imgTuple) -> Mono.fromCallable(() -> {
                            File finalFile = temp.resolve(cid + ".png").toFile();
                            ImageIO.write(imgTuple.getT1(), "png", finalFile);
                            return finalFile;
                        }).subscribeOn(Schedulers.boundedElastic()),

                        (ca, cid, temp, file, sTuple, imgTuple, finalFile) -> this.getFSService().createFileFromFile("SYSTEM",
                                        "_clientImages", finalFile.getName(), Paths.get(finalFile.getAbsolutePath()), true)
                                .<FileDetail>map(fd -> this.convertToFileDetailWhileCreation("/_clientImages", "SYSTEM", fd)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StaticFileResourceService.uploadClientImage"));
    }

    @Override
    protected Mono<Boolean> subClientListAccess(String resourcePath, String clientCode) {
        resourcePath = resourcePath.trim();
        if (resourcePath.isBlank()) return Mono.just(false);
        if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);
        int index = resourcePath.indexOf('/', 1);
        String firstFolderName = resourcePath.substring(resourcePath.startsWith("/") ? 1 : 0, index == -1 ? resourcePath.length() : index);

        if (!SPECIAL_FOLDERS.contains(firstFolderName)) return Mono.just(false);

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> switch (firstFolderName) {
                    case WITH_IN_SUB_CLIENT -> ca.getClientCode().equals(clientCode) ? Mono.just(false)
                            : this.securityService.isBeingManaged(clientCode, ca.getClientCode());
                    case ALL_SUB_CLIENTS -> this.securityService.isBeingManaged(clientCode, ca.getClientCode());
                    default -> Mono.just(false);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME,
                "SecuredFileResourceService.checkReadAccessWithClientCode"));

    }
}
