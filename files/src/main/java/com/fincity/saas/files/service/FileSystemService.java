package com.fincity.saas.files.service;

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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.FileType;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.files.dao.FileSystemDao;
import com.fincity.saas.files.jooq.enums.FilesFileSystemType;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.model.FilesPage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
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
    private final S3AsyncClient s3Client;
    private final FilesFileSystemType fileSystemType;

    private final Logger logger = LoggerFactory.getLogger(FileSystemService.class);

    public FileSystemService(FileSystemDao fileSystemDao, CacheService cacheService, String bucketName,
            S3AsyncClient s3Client, FilesFileSystemType fileSystemType) {
        this.fileSystemDao = fileSystemDao;
        this.cacheService = cacheService;
        this.bucketName = bucketName;
        this.s3Client = s3Client;
        this.fileSystemType = fileSystemType;
    }

    public Mono<Boolean> exists(String path) {
        int ind = path.indexOf(R2_FILE_SEPARATOR_CHAR);

        if (ind == -1)
            return Mono.just(true);

        return this.exists(path.substring(0, ind), path.substring(ind + 1));
    }

    public Mono<Boolean> exists(String clientCode, String path) {

        if (StringUtil.safeIsBlank(path) || path.equals(R2_FILE_SEPARATOR_STRING))
            return Mono.just(true);

        return cacheService.cacheValueOrGet(CACHE_NAME_EXISTS + "-" + clientCode,
                () -> this.fileSystemDao.exists(this.fileSystemType, clientCode, path),
                path);
    }

    public Mono<Page<FileDetail>> list(String clientCode, String path, FileType[] fileType, String filter,
            Pageable page) {

        return FlatMapUtil.flatMapMono(
                () -> {

                    if ((fileType != null && fileType.length > 0)
                            || !StringUtil.safeIsBlank(filter)
                            || (page.getSort() != null && (page.getSort().isEmpty() || page.getSort().isUnsorted()))
                            || page.getPageNumber() != 0
                            || page.getPageSize() != 200)
                        return this.fileSystemDao.list(this.fileSystemType, clientCode, path, fileType, filter, page);

                    return cacheService.<FilesPage>cacheValueOrGet(CACHE_NAME_LIST + "-" + clientCode,
                            () -> this.fileSystemDao.list(this.fileSystemType, clientCode, path, null, null, page),
                            path);
                },
                filesPage -> Mono
                        .just(PageableExecutionUtils.getPage(
                                filesPage.content().stream().map(FileDetail::clone).toList(), page,
                                filesPage::totalElements)))
                .switchIfEmpty(Mono.just(PageableExecutionUtils.getPage(new ArrayList<>(), page, () -> 0l)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, SERVICE_NAME_PREFIX + this.bucketName + ").list"));
    }

    public Mono<FileDetail> getFileDetail(String path) {
        int ind = path.indexOf(R2_FILE_SEPARATOR_CHAR);

        if (ind == -1)
            return this.getFileDetail(path, "");

        return this.getFileDetail(path.substring(0, ind), path.substring(ind + 1));
    }

    public Mono<FileDetail> getFileDetail(String clientCode, String path) {

        if (StringUtil.safeIsBlank(path))
            return Mono.just(new FileDetail().setFileName("").setDirectory(true));

        return cacheService.cacheValueOrGet(CACHE_NAME_GET_DETAIL + "-" + clientCode,
                () -> this.fileSystemDao.getFileDetail(this.fileSystemType, clientCode, path), path);
    }

    public Mono<File> getAsFile(String path) {

        path = path.replace("//", "/");

        if (StringUtil.safeIsBlank(path))
            return Mono.empty();

        try {
            Path folder = Files.createTempDirectory("fileDownload");
            String[] pathParts = path.split(R2_FILE_SEPARATOR_STRING);
            String fileName = pathParts[pathParts.length - 1];
            if (fileName.isBlank())
                fileName = pathParts[pathParts.length - 2];
            Path filePath = folder.resolve(fileName);
            return Mono.fromFuture(s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(path).build(),
                    AsyncResponseTransformer.toFile(filePath))).thenReturn(filePath.toFile());
        } catch (IOException ex) {
            return Mono.error(
                    new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to download file : " + path, ex));
        }
    }

    public Mono<File> getDirectoryAsArchive(String folderPath) {
        if (StringUtil.safeIsBlank(folderPath))
            return Mono.empty();

        folderPath = folderPath.endsWith(R2_FILE_SEPARATOR_STRING) ? folderPath
                : (folderPath + R2_FILE_SEPARATOR_STRING);
        if (folderPath.startsWith(R2_FILE_SEPARATOR_STRING))
            folderPath = folderPath.substring(1);

        String relPath = folderPath;
        try {
            Path folder = Files.createTempDirectory("folderDownload");
            Path filePath = folder.resolve("directory.zip");

            return Flux.from(s3Client.listObjectsV2Paginator(
                    ListObjectsV2Request.builder().bucket(bucketName)
                            .prefix(relPath)
                            .build()))
                    .flatMap(e -> Flux.fromIterable(e.contents()))
                    .index()
                    .flatMap(e -> Mono
                            .fromFuture(s3Client.getObject(
                                    GetObjectRequest.builder().bucket(bucketName).key(e.getT2().key()).build(),
                                    folder.resolve(e.getT1().toString())))
                            .map(resp -> e))
                    .collectList()
                    .flatMap(lst -> {

                        logger.info("lst : {}", lst);

                        int index = relPath.length();
                        if (relPath.endsWith(R2_FILE_SEPARATOR_STRING))
                            index--;

                        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + filePath.toUri().toString()),
                                Map.of("create", "true"));) {
                            for (Tuple2<Long, S3Object> tup : lst) {
                                Path here = fs.getPath(tup.getT2().key().substring(index));
                                Files.createDirectories(here.getParent());
                                Files.copy(folder.resolve(tup.getT1().toString()), here);
                            }

                            return Mono.just(filePath.toFile());
                        } catch (IOException ex) {
                            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Failed to download directory : " + relPath, ex);
                        }
                    }).subscribeOn(Schedulers.boundedElastic());
        } catch (IOException ex) {
            return Mono.error(
                    new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Failed to download directory : " + folderPath, ex));
        }
    }

    public Mono<Boolean> deleteFile(String path) {
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

    public Mono<FileDetail> createFileFromFilePart(String clientCode, String path, String fileName, FilePart fp,
            boolean override) {

        Flux<ByteBuffer> byteBuffer = fp.content().flatMapSequential(
                dataBuffer -> Flux.fromIterable(dataBuffer::readableByteBuffers));

        return this.createFileFromFluxDataBufferInternal(clientCode, path, fileName, byteBuffer, override);
    }

    public Mono<FileDetail> createFileFromFluxDataBuffer(String clientCode, String path, String fileName,
            Flux<DataBuffer> dataBuffer, boolean override) {

        Flux<ByteBuffer> byteBuffer = dataBuffer.flatMapSequential(
                d -> Flux.fromIterable(d::readableByteBuffers));

        return this.createFileFromFluxDataBufferInternal(clientCode, path, fileName, byteBuffer, override);
    }

    public Mono<FileDetail> createFileFromFile(String clientCode, String path, String fileName, Path file,
            boolean override) {

        Flux<ByteBuffer> byteBuffer = DataBufferUtils.readAsynchronousFileChannel(
                () -> AsynchronousFileChannel.open(file, StandardOpenOption.READ),
                DefaultDataBufferFactory.sharedInstance,
                4096).flatMapSequential(
                        dataBuffer -> Flux.fromIterable(dataBuffer::readableByteBuffers));
        return this.createFileFromFluxDataBufferInternal(clientCode, path, fileName, byteBuffer, override);
    }

    private Mono<FileDetail> createFileFromFluxDataBufferInternal(String clientCode, String path, String fileName,
            Flux<ByteBuffer> byteBuffer, boolean override) {

        String filePath = fileName == null ? path : (path + R2_FILE_SEPARATOR_STRING + fileName);

        return FlatMapUtil.flatMapMono(

                () -> this.exists(clientCode, filePath),

                exists -> {
                    Mono<Long> length = byteBuffer.reduce(0L, (acc, bb) -> acc + bb.remaining());
                    byteBuffer.repeat();
                    return length;
                },

                (exists, length) -> {
                    if (BooleanUtil.safeValueOf(exists) && !override)
                        return this.getFileDetail(clientCode, filePath);

                    String key = clientCode;

                    if (filePath.startsWith(R2_FILE_SEPARATOR_STRING))
                        key += filePath;
                    else
                        key += R2_FILE_SEPARATOR_STRING + filePath;

                    String mimeType = URLConnection.guessContentTypeFromName(fileName);

                    return Mono.fromFuture(s3Client.putObject(
                            PutObjectRequest.builder()
                                    .bucket(bucketName)
                                    .contentLength(length)
                                    .contentType(mimeType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mimeType)
                                    .contentDisposition("inline")
                                    .key(key)
                                    .build(),
                            AsyncRequestBody.fromPublisher(byteBuffer)))
                            .then(this.fileSystemDao.createOrUpdateFile(this.fileSystemType, clientCode, filePath,
                                    fileName, ULong.valueOf(length),
                                    exists && override))
                            .flatMap(this.evictCache(clientCode));
                })
                .flatMap(this.evictCache(clientCode))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        SERVICE_NAME_PREFIX + this.bucketName + ").createFileFromFluxDataBuffer"));
    }

    public Mono<Boolean> createFileForZipUpload(String clientCode, ULong folderId, String path, Path file,
            boolean override) {

        return FlatMapUtil.<Boolean, software.amazon.awssdk.services.s3.model.PutObjectResponse, Boolean>flatMapMonoWithNull(
                () -> this.exists(clientCode, path),

                exists -> {
                    if (BooleanUtil.safeValueOf(exists) && !override)
                        return Mono.empty();

                    return Mono.fromFuture(s3Client.putObject(
                            PutObjectRequest.builder()
                                    .bucket(bucketName)
                                    .contentDisposition(
                                            "attachment; filename=\"" + file.getFileName().toString() + "\"")
                                    .key(clientCode + R2_FILE_SEPARATOR_STRING + path)
                                    .build(),
                            AsyncRequestBody.fromFile(file)));
                },

                (exits, response) -> {
                    if (response == null)
                        return Mono.just(true);

                    long fileLength = 0l;

                    try {
                        fileLength = Files.size(file);
                    } catch (IOException ex) {
                        logger.error("Failed to get file size : {}", file.getFileName(), ex);
                    }

                    return this.fileSystemDao
                            .createOrUpdateFileForZipUpload(this.fileSystemType, clientCode, folderId, path,
                                    file.getFileName().toString(), ULong.valueOf(fileLength));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        SERVICE_NAME_PREFIX + this.bucketName + ").createFileForZipUpload"));
    }

    public <T> Function<T, Mono<T>> evictCache(String clientCode) {
        return (T t) -> this.cacheService.evictAll(CACHE_NAME_EXISTS + "-" + clientCode)
                .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_GET_DETAIL + "-" + clientCode))
                .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_LIST + "-" + clientCode))
                .map(e -> t);
    }

    public Mono<FileDetail> createFolder(String clientCode, String path) {

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

        return Flux.fromIterable(paths)
                .flatMapSequential(e -> this.fileSystemDao.getId(this.fileSystemType, clientCode, e)
                        .map(opId -> Tuples.of(e, opId)))
                .flatMap(tup -> {
                    if (tup.getT2().isPresent())
                        return Mono.just(tup.mapT2(Optional::get));
                    return this.fileSystemDao.createFolder(this.fileSystemType, clientCode, tup.getT1())
                            .map(id -> Tuples.of(tup.getT1(), id));
                }).collectList().flatMap(lst -> {
                    if (lst.isEmpty())
                        return Mono.empty();
                    return this.fileSystemDao.getFileDetail(this.fileSystemType, clientCode, path);
                });
    }

    public Mono<Map<String, ULong>> createFolders(String clientCode, List<String> paths) {

        return this.fileSystemDao.createFolders(this.fileSystemType, clientCode, paths);
    }
}
