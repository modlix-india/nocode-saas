package com.modlix.saas.files.service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.FileSystemUtils;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.util.LogUtil;
import com.modlix.saas.commons2.service.CacheService;
import com.modlix.saas.commons2.util.BooleanUtil;
import com.modlix.saas.commons2.util.FileType;
import com.modlix.saas.commons2.util.HashUtil;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.commons2.util.Tuples;
import com.modlix.saas.commons2.util.Tuples.Tuple2;
import com.modlix.saas.files.dao.FileSystemDao;
import com.modlix.saas.files.jooq.enums.FilesFileSystemType;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.model.FilesPage;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

// This service is used for both static and secured files.

public class FileSystemService {

    private static final String CACHE_NAME_EXISTS = "fsPathExists";
    private static final String CACHE_NAME_LIST = "fsPathList";
    private static final String CACHE_NAME_GET_DETAIL = "fsGetDetail";
    private static final String SERVICE_NAME_PREFIX = "FileSystemService(";

    public static final String R2_FILE_SEPARATOR_STRING = "/";
    public static final char R2_FILE_SEPARATOR_CHAR = '/';

    private final FileSystemDao fileSystemDao;
    private final CacheService cacheService;
    private final String bucketName;
    private final S3Client s3Client;
    private final FilesFileSystemType fileSystemType;

    @Getter
    private final Path tempFolder;

    private final Logger logger = LoggerFactory.getLogger(FileSystemService.class);

    public FileSystemService(FileSystemDao fileSystemDao, CacheService cacheService, String bucketName,
            S3Client s3Client, FilesFileSystemType fileSystemType) {
        this.fileSystemDao = fileSystemDao;
        this.cacheService = cacheService;
        this.bucketName = bucketName;
        this.s3Client = s3Client;
        this.fileSystemType = fileSystemType;
        try {
            this.tempFolder = Files.createTempDirectory("download-" + this.bucketName);
        } catch (IOException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error in creating temporary folder for : " + this.bucketName, e);
        }
    }

    public boolean exists(String path) {
        int ind = path.indexOf(R2_FILE_SEPARATOR_CHAR);

        if (ind == -1)
            return true;

        return this.exists(path.substring(0, ind), path.substring(ind + 1));
    }

    public boolean exists(String clientCode, String path) {

        if (StringUtil.safeIsBlank(path) || path.equals(R2_FILE_SEPARATOR_STRING))
            return true;

        return cacheService.cacheValueOrGet(CACHE_NAME_EXISTS + "-" + clientCode,
                () -> this.fileSystemDao.exists(this.fileSystemType, clientCode, path),
                path);
    }

    public Page<FileDetail> list(String clientCode, String path, FileType[] fileType, String filter,
            Pageable page) {

        FilesPage firstPage = null;

        if ((fileType != null && fileType.length > 0)
                || !StringUtil.safeIsBlank(filter)
                || (page.getSort().isEmpty() || page.getSort().isUnsorted())
                || page.getPageNumber() != 0
                || page.getPageSize() != 200)
            firstPage = this.fileSystemDao.list(this.fileSystemType, clientCode, path, fileType, filter, page);

        firstPage = cacheService.<FilesPage>cacheValueOrGet(CACHE_NAME_LIST + "-" + clientCode,
                () -> this.fileSystemDao.list(this.fileSystemType, clientCode, path, null, null, page),
                path);

        if (firstPage == null)
            return PageableExecutionUtils.getPage(new ArrayList<>(), page, () -> 0L);

        return PageableExecutionUtils.getPage(
                firstPage.content().stream().map(FileDetail::clone).toList(), page,
                firstPage::totalElements);
    }

    public FileDetail getFileDetail(String path) {
        int ind = path.indexOf(R2_FILE_SEPARATOR_CHAR);

        if (ind == -1)
            return this.getFileDetail(path, "");

        return this.getFileDetail(path.substring(0, ind), path.substring(ind + 1));
    }

    public FileDetail getFileDetail(String clientCode, String path) {

        if (StringUtil.safeIsBlank(path))
            return new FileDetail().setFileName("").setDirectory(true);

        return cacheService.cacheValueOrGet(CACHE_NAME_GET_DETAIL + "-" + clientCode,
                () -> this.fileSystemDao.getFileDetail(this.fileSystemType, clientCode, path), path);
    }

    public File getAsFile(String path, boolean forceDownload) {

        Path finalPath = Path.of(path.replace("//", "/"));

        if (StringUtil.safeIsBlank(finalPath))
            return null;

        Path filePath = this.createPathInTempFolder(finalPath).block();

        boolean exists = forceDownload ? false : Files.exists(filePath);

        if (exists)
            return filePath.toFile();

        s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(finalPath.toString())
                        .build(),
                ResponseTransformer.toFile(filePath));

        return filePath.toFile();
    }

    public File getDirectoryAsArchive(String fp) {

        if (StringUtil.safeIsBlank(fp))
            return null;

        String folderPath = fp.endsWith(R2_FILE_SEPARATOR_STRING) ? fp : (fp + R2_FILE_SEPARATOR_STRING);
        if (folderPath.startsWith(R2_FILE_SEPARATOR_STRING))
            folderPath = folderPath.substring(1);

        String relPath = folderPath;
        Path folder;
        try {
            folder = Files.createTempDirectory("folderDownload");
        } catch (IOException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error in creating temporary folder for : folderDownload", e);
        }
        Path filePath = folder.resolve("directory.zip");

        return this.getDirectoryAsFile(relPath, folder, filePath);
    }

    private File getDirectoryAsFile(String relPath, Path folder, Path filePath) {

        var iterable = s3Client.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucketName)
                        .prefix(relPath)
                        .build())
                .contents();
        int i = 0;

        List<com.modlix.saas.commons2.util.Tuples.Tuple2<Integer, S3Object>> lst = new ArrayList<>();

        for (S3Object s3Object : iterable) {
            var response = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build(),
                    ResponseTransformer.toFile(folder.resolve("" + i)));
            i++;
            lst.add(Tuples.of(i, s3Object));
        }

        int index = relPath.length();
        if (relPath.endsWith(R2_FILE_SEPARATOR_STRING))
            index--;

        final int finalIndex = index;

        Map<String, String> map = new HashMap<>();
        if (!Files.exists(filePath))
            map.put("create", "true");

        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + filePath.toUri()), map)) {
            for (Tuple2<Integer, S3Object> tup : lst) {
                Path here = fs.getPath(tup.getT2().key().substring(finalIndex));
                try {
                    if (!Files.exists(here.getParent()))
                        Files.createDirectories(here.getParent());
                } catch (Exception ex) {
                    logger.error("Error in creating directory : {} for : {}", here.getParent(), here,
                            ex);
                }
                try {
                    Files.copy(folder.resolve(tup.getT1().toString()), here,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ex) {
                    logger.error("Error in copying file : {} to : {}", here,
                            folder.resolve(tup.getT1().toString()), ex);
                }
            }
        } catch (IOException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error in creating directory : " + filePath.toString(), e);
        }
        return filePath.toFile();
    }

    public boolean deleteFile(String path) {
        int ind = path.indexOf(R2_FILE_SEPARATOR_CHAR);

        String clientCode;
        String folderPath = "";

        if (ind != -1) {
            clientCode = path.substring(0, ind);
            folderPath = path.substring(ind + 1);
        } else {
            clientCode = path;
        }

        String finPath = folderPath;

        List<Boolean> deleted = s3Client.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucketName)
                        .prefix(path)
                        .build())
                .contents()
                .map(e -> {
                    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(e.key()).build()).get();
                    return true;
                }).block();

        return Flux.from(s3Client.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucketName)
                        .prefix(path)
                        .build()))
                .flatMap(e -> Flux.fromIterable(e.contents()))
                .flatMap(e -> Mono.fromFuture(
                        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(e.key()).build())))
                .then(Mono.just(true))
                .flatMap(e -> this.fileSystemDao.deleteFile(this.fileSystemType, clientCode, finPath))
                .flatMap(this.evictCache(clientCode));
    }

    public Mono<FileDetail> createFilesFromFilePart(String clientCode, String path, String fileName, FilePart fp,
            boolean override) {

        Flux<ByteBuffer> byteBuffer = fp.content().flatMapSequential(
                dataBuffer -> Flux.fromIterable(dataBuffer::readableByteBuffers));

        return this.createFileFromFluxDataBufferInternal(clientCode, path, fileName, byteBuffer, override, "inline");
    }

    public Mono<FileDetail> createFileFromFile(String clientCode, String path, String fileName, Path file,
            boolean override, String contentDisposition) {

        Flux<ByteBuffer> byteBuffer = DataBufferUtils.readAsynchronousFileChannel(
                () -> AsynchronousFileChannel.open(file, StandardOpenOption.READ),
                DefaultDataBufferFactory.sharedInstance,
                4096).flatMapSequential(
                        dataBuffer -> Flux.fromIterable(dataBuffer::readableByteBuffers));
        return this.createFileFromFluxDataBufferInternal(clientCode, path, fileName, byteBuffer, override,
                contentDisposition);
    }

    public Mono<FileDetail> createFileFromFile(String clientCode, String path, String fileName, Path file,
            boolean override) {

        return this.createFileFromFile(clientCode, path, fileName, file, override, "inline");
    }

    private Mono<FileDetail> createFileFromFluxDataBufferInternal(String clientCode, String path, String fileName,
            Flux<ByteBuffer> byteBuffer, boolean override, String contentDisposition) {

        String filePath = fileName == null ? path : (path + R2_FILE_SEPARATOR_STRING + fileName);

        return FlatMapUtil.flatMapMono(

                () -> this.exists(clientCode, filePath),

                exists -> byteBuffer.reduce(0L, (acc, bb) -> acc + bb.remaining()),

                (exists, length) -> {
                    if (BooleanUtil.safeValueOf(exists) && !override)
                        return this.getFileDetail(clientCode, filePath);

                    String key = clientCode;

                    if (filePath.startsWith(R2_FILE_SEPARATOR_STRING))
                        key += filePath;
                    else
                        key += R2_FILE_SEPARATOR_STRING + filePath;

                    String mimeType = URLConnection.guessContentTypeFromName(filePath);

                    String finalKey = key;
                    return Mono.fromFuture(s3Client.putObject(
                            PutObjectRequest.builder()
                                    .bucket(bucketName)
                                    .contentLength(length)
                                    .contentType(mimeType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mimeType)
                                    .contentDisposition(contentDisposition)
                                    .key(finalKey)
                                    .build(),
                            AsyncRequestBody.fromPublisher(byteBuffer)))
                            .then(this.fileSystemDao.createOrUpdateFile(this.fileSystemType, clientCode, filePath,
                                    fileName, ULong.valueOf(length),
                                    exists && override))
                            .flatMap(e -> Mono.fromCallable(() -> {
                                Files.deleteIfExists(this.tempFolder.resolve(HashUtil.sha256Hash(finalKey)));
                                return e;
                            }).subscribeOn(Schedulers.boundedElastic()));
                })
                .flatMap(this.evictCache(clientCode))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        SERVICE_NAME_PREFIX + this.bucketName + ").createFileFromFluxDataBuffer"))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public boolean createFileForZipUpload(String clientCode, ULong folderId, String path, Path file,
            boolean override) {

        return FlatMapUtil.<Boolean, software.amazon.awssdk.services.s3.model.PutObjectResponse, Boolean>flatMapMonoWithNull(
                () -> this.exists(clientCode, path),

                exists -> {
                    if (BooleanUtil.safeValueOf(exists) && !override)
                        return Mono.empty();

                    logger.info("Uploading the file : {} : ",
                            (clientCode + R2_FILE_SEPARATOR_STRING + path).replace("//", "/"));

                    String finalKey = (clientCode + R2_FILE_SEPARATOR_STRING + path).replace("//", "/");
                    return Mono.fromFuture(s3Client.putObject(
                            PutObjectRequest.builder()
                                    .bucket(bucketName)
                                    .contentDisposition("inline")
                                    .key(finalKey)
                                    .build(),
                            AsyncRequestBody.fromFile(file)))
                            .flatMap(e -> Mono.fromCallable(() -> {
                                Files.deleteIfExists(this.tempFolder.resolve(HashUtil.sha256Hash(finalKey)));
                                return e;
                            }).subscribeOn(Schedulers.boundedElastic()));
                },

                (exits, response) -> {
                    if (response == null)
                        return Mono.just(true);

                    return Mono.fromCallable(() -> Files.size(file))
                            .onErrorResume(ex -> Mono.just(0L))
                            .flatMap(fileLength -> this.fileSystemDao
                                    .createOrUpdateFileForZipUpload(this.fileSystemType, clientCode, folderId, path,
                                            file.getFileName().toString(), ULong.valueOf(fileLength)))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        SERVICE_NAME_PREFIX + this.bucketName + ").createFileForZipUpload"));
    }

    public <T> Function<T, T> evictCache(String clientCode) {
        return (T t) -> {
            this.cacheService.evictAll(CACHE_NAME_EXISTS + "-" + clientCode);
            this.cacheService.evictAll(CACHE_NAME_GET_DETAIL + "-" + clientCode);
            this.cacheService.evictAll(CACHE_NAME_LIST + "-" + clientCode);
            return t;
        };
    }

    public FileDetail createFolder(String clientCode, String path) {

        String resourcePath = path.startsWith(R2_FILE_SEPARATOR_STRING) ? path.substring(1) : path;

        List<String> paths = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        boolean notFirstTime = false;

        for (String part : resourcePath.split(R2_FILE_SEPARATOR_STRING)) {

            if (notFirstTime)
                sb.append(R2_FILE_SEPARATOR_STRING);

            sb.append(part);
            paths.add(sb.toString());
            notFirstTime = true;
        }

        for (String p : paths) {

            Optional<ULong> opId = this.fileSystemDao.getId(this.fileSystemType, clientCode, p);
            if (opId.isPresent())
                continue;

            this.fileSystemDao.createFolder(this.fileSystemType, clientCode, p);
        }

        return this.fileSystemDao.getFileDetail(this.fileSystemType, clientCode, path);
    }

    public Map<String, ULong> createFolders(String clientCode, List<String> paths) {

        return this.fileSystemDao.createFolders(this.fileSystemType, clientCode, paths);
    }

    private Path createPathInTempFolder(Path filePath) {

        Path targetPath = this.tempFolder.resolve(
                Path.of(HashUtil.sha256Hash(filePath.getParent()), filePath.getFileName().toString()));
        try {
            Files.createDirectories(targetPath.getParent());
            return targetPath;
        } catch (IOException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error in creating temporary folder for : " + targetPath.toString(), e);
        }
    }

    private void deleteTempFolderPath(Path filePath) {
        Path targetPath = this.tempFolder.resolve(
                Path.of(HashUtil.sha256Hash(filePath.getParent()), filePath.getFileName().toString()));
        FileSystemUtils.deleteRecursively(targetPath.toFile());
    }
}
