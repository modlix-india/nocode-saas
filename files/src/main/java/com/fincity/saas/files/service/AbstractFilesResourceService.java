package com.fincity.saas.files.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.*;
import com.fincity.saas.files.dto.FilesUploadDownloadDTO;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.jooq.enums.FilesUploadDownloadResourceType;
import com.fincity.saas.files.jooq.enums.FilesUploadDownloadType;
import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.model.ImageDetails;
import com.fincity.saas.files.tasks.FilesUploadDownloadRunnable;
import com.fincity.saas.files.util.FileExtensionUtil;
import com.fincity.saas.files.util.ImageTransformUtil;
import org.apache.commons.lang3.ObjectUtils;
import org.imgscalr.Scalr;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.http.codec.ResourceHttpMessageWriter;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.fincity.saas.files.service.FileSystemService.R2_FILE_SEPARATOR_STRING;

public abstract class AbstractFilesResourceService {

    private static final String GENERIC_URI_PART = "api/files/";

    private static final String GENERIC_URI_PART_FILE = "/file";

    private static final String GENERIC_URI_PART_IMPORT = "/import";

    private static final String INTERNAL_URI_PART = "/internal";

    private static final String STATIC_TYPE = "static";

    private static final String SECURED_TYPE = "secured";

    private static final String TRANSFORM_TYPE = "transform";

    private static final String GENERIC_URI_PART_STATIC = GENERIC_URI_PART + STATIC_TYPE;

    private static final String GENERIC_URI_PART_SECURED = GENERIC_URI_PART + SECURED_TYPE;

    private static final Logger logger = LoggerFactory.getLogger(AbstractFilesResourceService.class);

    protected final FilesMessageResourceService msgService;
    protected final FilesAccessPathService fileAccessService;
    protected final FilesUploadDownloadService filesUploadDownloadService;

    private final ExecutorService virtualThreadExecutor;

    protected AbstractFilesResourceService(
        FilesAccessPathService fileAccessService, FilesMessageResourceService msgService,
        FilesUploadDownloadService filesUploadDownloadService) {
        this.msgService = msgService;
        this.fileAccessService = fileAccessService;
        this.filesUploadDownloadService = filesUploadDownloadService;

        this.virtualThreadExecutor = new ThreadPoolExecutor(
            30,
            300,
            10L,
            TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(),
            Thread.ofVirtual().factory());
    }

    private String uriPart;
    private String uriPartFile;
    private String uriPartImport;

    private record PathParts(String resourcePath, String clientCode, String fileName) {
    }

    protected void initialize() {

        String type = this.getResourceType()
            .toLowerCase();

        this.uriPart = GENERIC_URI_PART + type;

        this.uriPartFile = GENERIC_URI_PART + type + GENERIC_URI_PART_FILE;

        this.uriPartImport = GENERIC_URI_PART + type + GENERIC_URI_PART_IMPORT;
    }

    public Mono<Page<FileDetail>> list(String clientCode, String uri, FileType[] fileType, String filter,
                                       Pageable page) {

        Tuple2<String, String> tup = this.resolvePathWithoutClientCode(this.uriPart, uri);
        String resourcePath = tup.getT1();

        return FlatMapUtil.flatMapMono(

                () -> this.fileAccessService.hasReadAccess(resourcePath, clientCode,
                    FilesAccessPathResourceType.valueOf(this.getResourceType())),

                hasPermission -> {

                    if (!BooleanUtil.safeValueOf(hasPermission))
                        return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                            FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath);

                    return this.getFSService().list(clientCode, resourcePath, fileType, filter, page);
                },

                (hasPermission, dataPage) -> {

                    String folderPath = (resourcePath.startsWith(R2_FILE_SEPARATOR_STRING)
                        || StringUtil.safeIsBlank(resourcePath)) ? resourcePath
                        : (R2_FILE_SEPARATOR_STRING + resourcePath);

                    for (FileDetail fd : dataPage.getContent()) {
                        fd.setFilePath(folderPath + R2_FILE_SEPARATOR_STRING + fd.getName())
                            .setUrl(this.getResourceFileType() + (fd.isDirectory() ? "" : "/file/" + clientCode)
                                + folderPath
                                + R2_FILE_SEPARATOR_STRING
                                + URLEncoder.encode(fd.getName(), StandardCharsets.UTF_8));
                    }
                    return Mono.just(dataPage);
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.list"));
    }

    private String getResourceFileType() {

        return this.getResourceType()
            .equals(FilesAccessPathResourceType.STATIC.name()) ? GENERIC_URI_PART_STATIC : GENERIC_URI_PART_SECURED;

    }

    private FileDetail convertToFileDetailWhileCreation(String resourcePath, String clientCode, FileDetail fileDetail) {

        String resourceType = this.getResourceFileType();

        if (!resourcePath.startsWith(R2_FILE_SEPARATOR_STRING))
            resourcePath = R2_FILE_SEPARATOR_STRING + resourcePath;

        if (fileDetail.isDirectory()) {
            fileDetail.setFilePath(resourcePath)
                .setUrl(resourceType + resourcePath)
                .setDirectory(true);

        } else {
            fileDetail.setFilePath(resourcePath + R2_FILE_SEPARATOR_STRING + fileDetail.getName())
                .setUrl(resourceType + ("/file/" + clientCode) + resourcePath + R2_FILE_SEPARATOR_STRING
                    + URLEncoder.encode(fileDetail.getName(), StandardCharsets.UTF_8));
        }
        return fileDetail;

    }

    public Mono<Void> downloadFile(DownloadOptions downloadOptions, ServerHttpRequest request,
                                   ServerHttpResponse response) {

        String rp = this.resolvePathWithClientCode(request.getURI().toString()).getT1();

        return FlatMapUtil.flatMapMono(

                () -> this.checkReadAccessWithClientCode(rp),

                hasAccess -> {
                    if (!BooleanUtil.safeValueOf(hasAccess))
                        return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                            FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), rp);

                    return this.getFSService().getFileDetail(rp);
                },
                (hasAccess, fd) -> downloadFileByFileDetails(fd, downloadOptions, rp, request, response))
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.downloadFile"));

    }

    /**
     * @param resourcePath the path of the resource to which the access need to be
     *                     checked.
     * @return a Mono if the readAccess is granted by the requester
     */
    protected Mono<Boolean> checkReadAccessWithClientCode(String resourcePath) {
        return Mono.just(true);
    }

    protected Mono<Void> makeMatchesStartDownload(DownloadOptions downloadOptions, ServerHttpRequest request,
                                                  ServerHttpResponse response, boolean isDirectory, String path, long fileMillis, String fileETag) {
        var respHeaders = response.getHeaders();
        var reqHeaders = request.getHeaders();

        if (BooleanUtil.safeValueOf(downloadOptions.getNoCache()))
            return sendFile(downloadOptions, fileETag, fileMillis, isDirectory, path, request, response);

        long modifiedSince = reqHeaders.getIfModifiedSince();
        if (modifiedSince != -1 && fileMillis == modifiedSince) {
            return sendHitResponse(respHeaders, response);
        }

        String eTag = reqHeaders.getETag();
        if (eTag == null) {
            List<String> matches = reqHeaders.getIfNoneMatch();
            if (!matches.isEmpty())
                eTag = matches.getFirst();
        }

        if (fileETag.equals(eTag))
            return sendHitResponse(respHeaders, response);

        return sendFile(downloadOptions, fileETag, fileMillis, isDirectory, path, request, response);
    }

    private Mono<Void> sendHitResponse(HttpHeaders respHeaders, ServerHttpResponse response) {

        respHeaders.set("x-cache", "HIT");
        respHeaders.setContentLength(0L);
        response.setStatusCode(HttpStatusCode.valueOf(304));
        return response.setComplete();
    }

    public Mono<Void> sendFile(DownloadOptions downloadOptions, String eTag, long fileMillis, boolean isDirectory,
                               String path,
                               ServerHttpRequest request, ServerHttpResponse response) {

        HttpHeaders respHeaders = response.getHeaders();

        String[] pathParts = path.split(FileSystemService.R2_FILE_SEPARATOR_STRING);
        String fileName = pathParts[pathParts.length - 1];
        if (StringUtil.safeIsBlank(fileName) && pathParts.length > 1)
            fileName = pathParts[pathParts.length - 2];
        if (StringUtil.safeIsBlank(fileName))
            fileName = "file";

        downloadOptions.setName(downloadOptions.getName() == null ? fileName : downloadOptions.getName());

        respHeaders.set("x-cache", "MISS");
        respHeaders.setLastModified(fileMillis);
        respHeaders.setETag(eTag);
        if (!BooleanUtil.safeValueOf(downloadOptions.getNoCache())
            && this.getResourceType().equals(FilesAccessPathResourceType.STATIC.name()))
            respHeaders.setCacheControl("public, max-age=3600");

        respHeaders.setContentDisposition(
            (BooleanUtil.safeValueOf(downloadOptions.getDownload()) ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(isDirectory ? downloadOptions.getName() + ".zip" : downloadOptions.getName())
                .build());
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        if (mimeType == null) {
            logger.debug("Unable to find mimetype of file {}", path);
            mimeType = "application/octet-stream";
        }
        respHeaders.setContentType(MediaType.valueOf(mimeType));

        Mono<File> actualFile;
        if (isDirectory) {
            downloadOptions.setDownload(true);
            actualFile = this.getFSService().getDirectoryAsArchive(path);
        } else {
            actualFile = this.getFSService().getAsFile(path);
        }

        return FlatMapUtil.flatMapMono(
                () -> actualFile,

                af -> {

                    long length = af.length();

                    List<HttpRange> ranges = request.getHeaders()
                        .getRange();

                    if (ranges.isEmpty()) {
                        return sendFileWhenNoRanges(downloadOptions, response, respHeaders, af, length);
                    } else {
                        return sendFileWhenRanges(downloadOptions, request, response, af);
                    }
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.sendFile"));

    }

    private Mono<Void> sendFileWhenRanges(DownloadOptions downloadOptions, ServerHttpRequest request,
                                          ServerHttpResponse response, File actualFile) {

        ResourceHttpMessageWriter writer = new ResourceHttpMessageWriter();
        if (!downloadOptions.hasModification()) {
            return writer.write(Mono.just(new FileSystemResource(actualFile)), null,
                ResolvableType.forClass(File.class), null, request, response, Map.of());
        } else {
            byte[] bytes = this.applyOptionsMakeResource(downloadOptions, actualFile);

            if (bytes.length == 0)
                return writer.write(Mono.just(new FileSystemResource(actualFile)), null,
                    ResolvableType.forClass(File.class), null, request, response, Map.of());

            return writer.write(Mono.just(new ByteArrayResource(bytes)), null, ResolvableType.forClass(File.class),
                null, request, response, Map.of());
        }
    }

    private Mono<Void> sendFileWhenNoRanges(DownloadOptions downloadOptions,
                                            ServerHttpResponse response,
                                            HttpHeaders respHeaders, File actualFile, long length) {

        ZeroCopyHttpOutputMessage zeroCopyResponse = (ZeroCopyHttpOutputMessage) response;
        if (!downloadOptions.hasModification()) {

            respHeaders.setRange(List.of(HttpRange.createByteRange(0, length - 1)));
            respHeaders.setContentLength(length);
            return zeroCopyResponse.writeWith(actualFile, 0, length);
        } else {

            byte[] bytes = this.applyOptionsMakeResource(downloadOptions, actualFile);

            if (bytes.length == 0) {
                respHeaders.setRange(List.of(HttpRange.createByteRange(0, length - 1)));
                respHeaders.setContentLength(length);
                return zeroCopyResponse.writeWith(actualFile, 0, length);
            }

            respHeaders.setRange(List.of(HttpRange.createByteRange(0, bytes.length - 1L)));
            respHeaders.setContentLength(bytes.length);

            return zeroCopyResponse.writeWith(Mono.just(response.bufferFactory()
                .wrap(bytes)));
        }
    }

    private String imageType(String fileName) {
        if (StringUtil.safeIsBlank(fileName))
            return "jpg";
        int index = fileName.lastIndexOf('.');
        if (index == -1)
            return "jpg";
        return fileName.substring(index + 1);
    }

    private byte[] applyOptionsMakeResource(DownloadOptions options, File file) {

        try {

            BufferedImage image = makeImage(options, file);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            boolean didItWrite = ImageIO.write(image, imageType(file.getName()), byteArrayOutputStream);
            if (!didItWrite)
                throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to write the image.");
            image.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException | GenericException | NullPointerException ex) {
            logger.debug("Image resize issue", ex);
            return new byte[0];
        }
    }

    private BufferedImage makeImage(DownloadOptions options, File file) throws IOException {

        BufferedImage image = ImageIO.read(file);

        if (image == null) throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read the image.");

        Scalr.Mode scalingMode = BooleanUtil.safeValueOf(options.getKeepAspectRatio()) ? Scalr.Mode.FIT_TO_WIDTH
            : Scalr.Mode.FIT_EXACT;
        if (scalingMode != Scalr.Mode.FIT_EXACT
            && options.getResizeDirection() == DownloadOptions.ResizeDirection.VERTICAL) {
            scalingMode = Scalr.Mode.FIT_TO_HEIGHT;
        }

        BufferedImage resizedImage = Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, scalingMode,
            options.getWidth() == null ? image.getWidth() : options.getWidth(),
            options.getHeight() == null ? image.getHeight() : options.getHeight(), Scalr.OP_ANTIALIAS);

        if (!BooleanUtil.safeValueOf(options.getKeepAspectRatio())
            || StringUtil.safeIsBlank(options.getBandColor()))

            return resizedImage;

        return applyBands(options, resizedImage, scalingMode);
    }

    private BufferedImage applyBands(DownloadOptions options, BufferedImage image, Scalr.Mode scalingMode) {

        if ((scalingMode == Scalr.Mode.FIT_TO_WIDTH && options.getHeight() != null)
            || (scalingMode == Scalr.Mode.FIT_TO_HEIGHT && options.getWidth() != null)) {

            int optionWidth = options.getWidth() == null ? image.getWidth() : options.getWidth();
            int optionHeight = options.getHeight() == null ? image.getHeight() : options.getHeight();

            BufferedImage bImage = new BufferedImage(
                scalingMode == Scalr.Mode.FIT_TO_HEIGHT ? optionWidth : image.getWidth(),
                scalingMode == Scalr.Mode.FIT_TO_WIDTH ? optionHeight : image.getHeight(),
                BufferedImage.TYPE_INT_RGB);

            Graphics2D g2d = bImage.createGraphics();

            g2d.setColor(java.awt.Color.decode(options.getBandColor()
                .startsWith("#") ? options.getBandColor() : "#" + options.getBandColor()));
            g2d.fillRect(0, 0, bImage.getWidth(), bImage.getHeight());
            g2d.drawImage(image,
                scalingMode == Scalr.Mode.FIT_TO_WIDTH ? 0 : (bImage.getWidth() - image.getWidth()) / 2,
                scalingMode == Scalr.Mode.FIT_TO_HEIGHT ? 0 : (bImage.getHeight() - image.getHeight()) / 2, null);

            g2d.dispose();
            return bImage;
        }

        return image;
    }

    public Mono<Boolean> delete(String clientCode, String uri) {

        Tuple2<String, String> tup = this.resolvePathWithoutClientCode(this.uriPart, uri);
        String resourcePath = tup.getT1();

        return FlatMapUtil.flatMapMono(

                () -> this.fileAccessService.hasWriteAccess(resourcePath, clientCode,
                    FilesAccessPathResourceType.valueOf(this.getResourceType())),

                hasPermission -> {

                    if (!BooleanUtil.safeValueOf(hasPermission)) {
                        return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                            FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath);
                    }

                    return this.getFSService()
                        .deleteFile(clientCode + FileSystemService.R2_FILE_SEPARATOR_STRING + resourcePath);
                }

            )
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.delete"));
    }

    public Mono<Object> create(String clientCode, String uri, Flux<FilePart> fileParts, String fileName, Boolean override) {

        boolean ovr = override == null || BooleanUtil.safeValueOf(override);
        Tuple2<String, String> tup = this.resolvePathWithoutClientCode(this.uriPart, uri);
        String resourcePath = tup.getT1();
        String urlResourcePath = tup.getT2();

        return FlatMapUtil.flatMapMonoWithNull(

                () -> this.fileAccessService.hasWriteAccess(resourcePath, clientCode,
                    FilesAccessPathResourceType.valueOf(this.getResourceType())),

                hasPermission -> {

                    if (!BooleanUtil.safeValueOf(hasPermission))
                        return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                            FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath);

                    if (fileParts == null) {
                        return this.getFSService().createFolder(clientCode, resourcePath).map(Object.class::cast);
                    }

                    return fileParts.flatMap(fp -> {
                        String fn = fileName == null ? fp.filename()
                            : FileExtensionUtil.getFileNameWithExtension(fp.filename(), fileName);

                        return this.getFSService().createFilesFromFilePart(clientCode, resourcePath, fn, fp, ovr)
                            .map(d -> this.convertToFileDetailWhileCreation(urlResourcePath, clientCode, d));
                    }).collectList().map(e -> {
                        if (e.size() > 1) return e;
                        return e.getFirst();
                    });
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
    }

    private PathParts extractPathClientCodeFileName(String uri, FilePart fp, String filePath,
                                                    String clientCode) {
        String resourcePath;
        String fileName = "";

        if (fp != null) {
            resourcePath = this.resolvePathWithoutClientCode(this.uriPart, uri).getT1();
            fileName = fp.filename();
        } else {
            resourcePath = this.resolvePathWithClientCode(filePath).getT1();
            String[] pathParts = resourcePath.split(R2_FILE_SEPARATOR_STRING);
            for (String pathPart : pathParts) {
                if (!StringUtil.safeIsBlank(pathPart)) {
                    clientCode = pathPart;
                    break;
                }
            }

            for (int i = pathParts.length - 1; i >= 0; i--) {
                if (!StringUtil.safeIsBlank(pathParts[i])) {
                    fileName = pathParts[i];
                    break;
                }
            }

            // Removing the client code from the resource path
            resourcePath = resourcePath.substring(resourcePath.indexOf(clientCode) + clientCode.length());
            // Removing the file name from the resource path
            resourcePath = resourcePath.substring(0, resourcePath.lastIndexOf(fileName));
        }

        if (resourcePath.startsWith(R2_FILE_SEPARATOR_STRING))
            resourcePath = resourcePath.substring(1);

        return new PathParts(resourcePath, clientCode, fileName);
    }

    public Mono<FileDetail> imageUpload(String clientCode, String uri, FilePart fp, String fileName, Boolean override,
                                        ImageDetails imageDetails, String filePath) {
        boolean ovr = override == null || BooleanUtil.safeValueOf(override);

        int ind = uri.indexOf(TRANSFORM_TYPE);
        // just removing the TRANSFORM_TYPE from the uri
        if (ind != -1) {
            uri = uri.substring(0, ind) + uri.substring(ind + TRANSFORM_TYPE.length() + 1);
        }

        PathParts pathParts = this.extractPathClientCodeFileName(uri, fp, filePath, clientCode);


        return FlatMapUtil.flatMapMono(

            () -> this.fileAccessService.hasWriteAccess(pathParts.resourcePath, pathParts.clientCode,
                FilesAccessPathResourceType.valueOf(this.getResourceType())),

            hasPermission -> Mono.fromCallable(() -> Files.createTempDirectory("imageUpload"))
                .subscribeOn(Schedulers.boundedElastic()),

            (hasPermission, tempDirectory) -> {
                if (!BooleanUtil.safeValueOf(hasPermission))
                    return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        FilesMessageResourceService.FORBIDDEN_PATH, getResourceType(), pathParts.fileName);

                if (fp != null) {
                    Path file = tempDirectory.resolve(pathParts.fileName);
                    return fp.transferTo(file).thenReturn(file.toFile());
                }

                return this.getFSService()
                    .getAsFile(pathParts.clientCode + FileSystemService.R2_FILE_SEPARATOR_STRING
                        + pathParts.resourcePath + FileSystemService.R2_FILE_SEPARATOR_STRING
                        + pathParts.fileName);
            },

            (hasPermission, tempDirectory, file) -> Mono.fromCallable(() -> this.makeSourceImage(file, pathParts)).subscribeOn(Schedulers.boundedElastic()),

            (hasPermission, tempDirectory, file, sourceTuple) -> {

                int type = sourceTuple.getT2();

                if (fp != null && !StringUtil.safeIsBlank(fileName)) {
                    type = fileName.toLowerCase().endsWith("png") ? BufferedImage.TYPE_INT_ARGB
                        : BufferedImage.TYPE_INT_RGB;
                }

                final int finalImageType = type;
                return Mono
                    .defer(() -> Mono.just(Tuples.of(
                        ImageTransformUtil.transformImage(sourceTuple.getT1(), finalImageType,
                            imageDetails),
                        finalImageType)))
                    .subscribeOn(Schedulers.boundedElastic());
            },

            (hasPermission, tempDirectory, file, sourceTuple, transformedTuple) -> this.finalFileWrite(pathParts,
                transformedTuple,
                fileName,
                ovr)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.imageUpload (File)"));


    }

    private Mono<FileDetail> finalFileWrite(PathParts pathParts,
                                            Tuple2<BufferedImage, Integer> transformedTuple, String fileName, boolean override) {

        return FlatMapUtil.flatMapMono(

            () -> Mono.fromCallable(() -> {
                Path tempDirectory = Files.createTempDirectory("imageUpload");
                Path path = tempDirectory.resolve(pathParts.fileName);
                File file = path.toFile();
                ImageIO.write(transformedTuple.getT1(), path.getFileName()
                    .toString()
                    .toLowerCase()
                    .endsWith("png") ? "png" : "jpeg", file);

                String filePath = (pathParts.resourcePath + FileSystemService.R2_FILE_SEPARATOR_STRING + fileName)
                    .replace("//", "/");
                return Tuples.of(path, filePath);
            }).subscribeOn(Schedulers.boundedElastic()),

            fileTup -> this.getFSService().createFileFromFile(pathParts.clientCode,
                fileTup.getT2(),
                null, fileTup.getT1(), override),

            (fileTup, fileDetail) -> Mono.just(this.convertToFileDetailWhileCreation(pathParts.resourcePath,
                pathParts.clientCode, fileDetail))


        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.finalFileWrite"));


    }

    private Tuple2<BufferedImage, Integer> makeSourceImage(File file,
                                                           PathParts pathParts) throws IOException {

        ImageInputStream iis = ImageIO.createImageInputStream(file);

        Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);

        if (imageReaders.hasNext()) {
            ImageReader reader = imageReaders.next();
            reader.setInput(iis);
            return Tuples.of(reader.read(0),
                pathParts.fileName.toLowerCase().endsWith("png") ? BufferedImage.TYPE_INT_ARGB
                    : BufferedImage.TYPE_INT_RGB);
        }

        throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to find the reader for the image.");
    }

    public Mono<ULong> createFromZipFile(String clientCode, String uri, FilePart fp, Boolean override) {

        boolean ovr = override == null || BooleanUtil.safeValueOf(override);
        Tuple2<String, String> tup = this.resolvePathWithoutClientCode(this.uriPartImport, uri);

        String resourcePath = tup.getT1();

        if (fp == null || (!fp.filename()
            .toLowerCase()
            .endsWith(".zip"))) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                FilesMessageResourceService.UNABLE_TO_READ_UP_FILE);
        }

        return FlatMapUtil.flatMapMono(

                () -> Mono.fromCallable(() -> {

                        Path tmpFile = Files.createTempFile("tmp", "zip");
                        Path tmpFolder = Files.createTempDirectory("tmp");

                        return Tuples.of(tmpFile, tmpFolder);
                    }).flatMap(tmpTup -> fp.transferTo(tmpTup.getT1()).then(Mono.just(tmpTup)))
                    .subscribeOn(Schedulers.boundedElastic()),

                tmpTup -> SecurityContextUtil.getUsersContextAuthentication(),

                (tmpTup, ca) -> {

                    FilesUploadDownloadDTO dto = new FilesUploadDownloadDTO();
                    dto.setCreatedBy(ULong.valueOf(ca.getUser().getId()));

                    return this.filesUploadDownloadService.create(dto
                        .setClientCode(clientCode)
                        .setPath(resourcePath)
                        .setType(FilesUploadDownloadType.UPLOAD)
                        .setResourceType(FilesUploadDownloadResourceType.valueOf(this.getResourceType())));
                },

                (tmpTup, ca, fud) -> {

                    Mono<?> deflateMono = this.deflateAndProcess(tmpTup, resourcePath, clientCode, ovr);
                    Mono<?> cacheEviction = Mono.defer(() -> this.getFSService().evictCache(clientCode).apply(Boolean.TRUE));

                    this.virtualThreadExecutor.submit(new FilesUploadDownloadRunnable(deflateMono, fud,
                        this.filesUploadDownloadService, cacheEviction));

                    return Mono.just(fud.getId());
                }

            ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.createFromZipFile"))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> deflateAndProcess(Tuple2<Path, Path> tmpTup, String resourcePath, String clientCode,
                                            boolean override) {
        boolean isRoot = StringUtil.safeIsBlank(resourcePath);
        return FlatMapUtil.flatMapMono(
            () -> Mono.just(this.deflateFilesFolders(tmpTup.getT1(), tmpTup.getT2())),

            filesFoldersTuple -> {
                List<String> folderList = new ArrayList<>();

                if (!isRoot)
                    folderList.add(resourcePath);

                for (String folder : filesFoldersTuple.getT2())
                    folderList.add(isRoot ? folder : (resourcePath + R2_FILE_SEPARATOR_STRING + folder));

                return this.getFSService().createFolders(clientCode, folderList);
            },

            (filesFoldersTuple, folderMap) ->
                Flux.fromIterable(filesFoldersTuple.getT1())
                    .buffer(10)
                    .delayElements(Duration.ofSeconds(2))
                    .flatMapSequential(tupList ->
                        Flux.fromIterable(tupList).flatMap(tup -> {
                            String parentPath = tup.getT1();
                            int index = parentPath.lastIndexOf(R2_FILE_SEPARATOR_STRING);
                            String folderPath = index == -1 ? "" : parentPath.substring(0, index);
                            if (!isRoot)
                                folderPath = StringUtil.safeIsBlank(folderPath) ? resourcePath
                                    : (resourcePath + R2_FILE_SEPARATOR_STRING + folderPath);

                            ULong folderId = folderMap.get(folderPath);
                            if (!folderPath.isEmpty() && folderId == null)
                                return Mono.defer(() -> this.msgService.throwMessage(
                                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                    FilesMessageResourceService.UNABLE_TO_UPLOAD_ZIP_FILE));

                            return this.getFSService().createFileForZipUpload(clientCode, folderId,
                                folderPath.isEmpty() ? tup.getT1()
                                    : (resourcePath + R2_FILE_SEPARATOR_STRING
                                    + tup.getT1()),
                                tup.getT2(), override);
                        }), 3)
                    .collectList()
                    .map(e -> true));
    }

    private Tuple2<List<Tuple2<String, Path>>, List<String>> deflateFilesFolders(Path tmpFile, Path tmpFolder) {

        List<Tuple2<String, Path>> files = new ArrayList<>();
        Set<String> folders = new HashSet<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tmpFile.toFile()))) {

            ZipEntry ze;

            while ((ze = zis.getNextEntry()) != null) {

                if (ze.isDirectory()) {
                    Files.createDirectories(tmpFolder.resolve(ze.getName()));
                    folders.add(ze.getName().endsWith(R2_FILE_SEPARATOR_STRING) ? ze.getName().substring(0,
                        ze.getName().length() - 1) : ze.getName());
                } else {
                    var file = tmpFolder.resolve(ze.getName());
                    String path = ze.getName();
                    int index = path.lastIndexOf(R2_FILE_SEPARATOR_STRING);
                    if (index != -1)
                        folders.add(path.substring(0, index));
                    Files.createDirectories(file.getParent());
                    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                        int len;
                        byte[] buffer = new byte[1024];
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        files.add(Tuples.of(ze.getName(), file));
                    }
                }

            }
        } catch (IOException e) {

            throw new GenericException(HttpStatus.BAD_REQUEST, "Unable to read zip file", e);
        }

        List<String> folderList = new ArrayList<>(folders);
        folderList.sort(String::compareTo);

        return Tuples.of(files, folderList);
    }

    protected Tuple2<String, String> resolvePathWithClientCode(String uri) {

        String path = uri.substring(uri.indexOf(this.uriPartFile) + this.uriPartFile.length());
        return makeTuple(path);
    }

    protected Tuple2<String, String> resolvePathWithoutClientCode(String part, String uri) {

        String path = uri.substring(uri.indexOf(part) + part.length(),
            uri.length() - (uri.endsWith(R2_FILE_SEPARATOR_STRING) ? 1 : 0));
        return makeTuple(path);
    }

    private static Tuple2<String, String> makeTuple(String path) {
        if (path.startsWith(R2_FILE_SEPARATOR_STRING))
            path = path.substring(1);
        String origPath = path;

        path = URLDecoder.decode(path.replace('+', ' '), StandardCharsets.UTF_8);

        int index = path.indexOf('?');
        if (index != -1)
            path = path.substring(0, index);

        if (path.endsWith(R2_FILE_SEPARATOR_STRING))
            path = path.substring(0, path.length() - 1);

        return Tuples.of(path, origPath);
    }

    public Mono<FileDetail> createInternal(String clientCode, boolean override, String filePath,
                                           String fileName, ServerHttpRequest request) {

        return Mono.fromCallable(() -> Files.createTempDirectory("tmp").resolve(fileName))
            .flatMap(tmpFile -> DataBufferUtils.write(request.getBody(), tmpFile, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
                .then(Mono.just(tmpFile))
                .flatMap(file -> this.getFSService().createFileFromFile(
                    clientCode, filePath, fileName, tmpFile, override))
                .map(file -> this.convertToFileDetailWhileCreation(filePath, clientCode, file))
            ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> readFileAsBase64(String clientCode, String uri, boolean metadataRequired) {

        Tuple2<String, String> uriPath = this.resolvePathWithClientCode(uri);

        String resourcePath = uriPath.getT1();

        int ind = resourcePath.charAt(0) == '/' ? 1 : 0;
        int secondInd = resourcePath.indexOf('/', ind);

        String clientCodeFromUrl = secondInd == -1 ? resourcePath.substring(ind)
            : resourcePath.substring(ind, secondInd);

        if (StringUtil.safeIsBlank(clientCodeFromUrl))
            return Mono.empty();

        return FlatMapUtil.flatMapMono(

            () -> this.fileAccessService.isClientBeingManaged(clientCode, clientCodeFromUrl),

            isManaged -> {
                if (!BooleanUtil.safeValueOf(isManaged))
                    return Mono.empty();

                return this.getFSService().getFileDetail(uriPath.getT1());
            },

            (isManaged, fileDetail) -> {

                if (fileDetail.isDirectory())
                    return Mono.empty();

                return this.getFSService().getAsFile(uriPath.getT1())
                    .flatMap(e -> {

                        Path filePath = e.toPath();

                        StringBuilder sb = new StringBuilder();

                        if (metadataRequired) {

                            String[] pathParts = filePath.toString()
                                .split(FileSystemService.R2_FILE_SEPARATOR_STRING);
                            String fileName = pathParts[pathParts.length - 1];
                            if (StringUtil.safeIsBlank(fileName) && pathParts.length > 1)
                                fileName = pathParts[pathParts.length - 2];
                            if (StringUtil.safeIsBlank(fileName))
                                fileName = "file";

                            String mimeType = URLConnection.guessContentTypeFromName(fileName);
                            if (mimeType == null) {
                                mimeType = "application/octet-stream";
                            }

                            sb.append("data:");
                            sb.append(mimeType);
                            sb.append(";name:");
                            sb.append(fileName);
                            sb.append(";base64,");
                        }
                        return Mono.fromCallable(() -> {
                            try {
                                byte[] bytes = Files.readAllBytes(filePath);
                                sb.append(Base64.getEncoder().encodeToString(bytes));
                                return sb.toString();
                            } catch (IOException ex) {
                                return null;
                            }
                        }).subscribeOn(Schedulers.boundedElastic());
                    })
                    .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                        FilesMessageResourceService.FILE_CANNOT_BE_CONVERTED));
            }).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.convertToBase64"));
    }

    public Mono<Void> readInternal(DownloadOptions downloadOptions, String filePath, ServerHttpRequest request,
                                   ServerHttpResponse response) {

        if (!filePath.startsWith(R2_FILE_SEPARATOR_STRING)) {
            filePath = R2_FILE_SEPARATOR_STRING + filePath;
        }

        String uri = request.getPath().toString().replace(INTERNAL_URI_PART, "") + filePath;

        String rp = this.resolvePathWithClientCode(uri).getT1();

        return FlatMapUtil.flatMapMono(

                () -> this.getFSService().getFileDetail(rp),

                fd -> downloadFileByFileDetails(fd, downloadOptions, rp, request, response))
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.readInternal"));
    }

    private Mono<Void> downloadFileByFileDetails(FileDetail fileDetail, DownloadOptions downloadOptions,
                                                 String resourcePath, ServerHttpRequest request, ServerHttpResponse response) {

        if (fileDetail.isDirectory())
            return Mono.empty();

        long fileMillis = fileDetail.getLastModifiedTime();
        String fileETag = "\"" +
            fileDetail.getName().hashCode() +
            '-' +
            fileMillis +
            '-' +
            downloadOptions.eTagCode() +
            '"';

        return makeMatchesStartDownload(downloadOptions, request, response, false, resourcePath, fileMillis,
            fileETag);
    }

    public Mono<ULong> asyncExportFolder(String clientCode, ServerHttpRequest request) {

        String uri = request.getPath().toString();
        String resourcePath = this.resolvePathWithoutClientCode("export", uri).getT1();

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> ca.isAuthenticated() ? Mono.just(ObjectUtils.firstNonNull(clientCode, ca.getClientCode()))
                    : Mono.empty(),

                (ca, cc) -> this.fileAccessService.hasReadAccess(resourcePath, cc,
                    FilesAccessPathResourceType.valueOf(this.getResourceType())),

                (ca, cc, hasAccess) -> {
                    if (!BooleanUtil.safeValueOf(hasAccess))
                        return Mono.<FilesUploadDownloadDTO>empty();

                    FilesUploadDownloadDTO dto = new FilesUploadDownloadDTO();
                    dto.setCreatedBy(ULong.valueOf(ca.getUser().getId()));

                    return this.filesUploadDownloadService.create(dto
                        .setClientCode(cc)
                        .setCdnUrl("_downloads/" + UniqueUtil.shortUUID() + ".zip")
                        .setPath(resourcePath)
                        .setType(FilesUploadDownloadType.DOWNLOAD)
                        .setResourceType(FilesUploadDownloadResourceType.valueOf(this.getResourceType())));
                },

                (ca, cc, hasAccess, fud) -> {

                    Mono<FileDetail> taskMono = this.getFSService()
                        .getDirectoryAsArchive(
                            fud.getClientCode() + FileSystemService.R2_FILE_SEPARATOR_STRING + fud.getPath())
                        .flatMap(archive -> {

                            Path path = Paths.get(archive.getAbsolutePath());

                            int lastIndex = fud.getPath().lastIndexOf('/');
                            String name = fud.getPath().substring(lastIndex + 1);

                            lastIndex = fud.getCdnUrl().lastIndexOf('/');
                            String cdnFileName = fud.getCdnUrl().substring(lastIndex + 1);

                            String cdnPath = fud.getCdnUrl().substring(0, lastIndex);

                            return this.getFSService().createFileFromFile(fud.getClientCode(),
                                cdnPath, cdnFileName, path, true,
                                "attachment; filename=\"" + name + ".zip\"");
                        });

                    this.virtualThreadExecutor.submit(
                        new FilesUploadDownloadRunnable(taskMono, fud, this.filesUploadDownloadService));

                    return Mono.just(fud.getId());
                }

            )
            .switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath))

            .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.asyncExportFolder"));
    }

    @PreDestroy
    public void shutdownExecutor() {
        logger.info("Shutting down executor...");
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.error("Executor did not terminate in the specified time.");
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Shutdown interrupted.", e);
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public Mono<Page<FilesUploadDownloadDTO>> listExportsImports(FilesUploadDownloadType type, String clientCode,
                                                                 Pageable page) {

        return FlatMapUtil.flatMapMono(
            SecurityContextUtil::getUsersContextAuthentication,

            ca -> ca.isAuthenticated() ? Mono.just(ObjectUtils.firstNonNull(clientCode, ca.getClientCode()))
                : Mono.empty(),

            (ca, cc) -> this.filesUploadDownloadService.readPageFilter(page,
                new ComplexCondition().setOperator(ComplexConditionOperator.AND).setConditions(List.of(

                    new FilterCondition().setField("clientCode").setValue(cc),
                    new FilterCondition().setField("type")
                        .setValue(type),
                    new FilterCondition().setField("resourceType").setValue(this.getResourceType()),
                    new FilterCondition().setField("createdBy").setValue(ca.getUser().getId()))))

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.listExports"));
    }

    public abstract FileSystemService getFSService();

    public abstract String getResourceType();
}
