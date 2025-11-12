package com.modlix.saas.files.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.service.CacheService;
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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.DownloadDirectoryRequest;

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
    private final S3AsyncClient s3AsyncClient;
    private final FilesFileSystemType fileSystemType;
    private final FilesMessageResourceService messageService;

    @Getter
    private final Path tempFolder;

    private final Logger logger = LoggerFactory.getLogger(FileSystemService.class);

    public FileSystemService(FileSystemDao fileSystemDao, CacheService cacheService, String bucketName,
            S3Client s3Client, S3AsyncClient s3AsyncClient, FilesFileSystemType fileSystemType,
            FilesMessageResourceService messageService) {
        this.fileSystemDao = fileSystemDao;
        this.cacheService = cacheService;
        this.bucketName = bucketName;
        this.s3Client = s3Client;
        this.s3AsyncClient = s3AsyncClient;
        this.messageService = messageService;
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

        return cacheService.cacheValueOrGet(CACHE_NAME_EXISTS + "-" + this.fileSystemType.name() + "-" + clientCode,
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

        firstPage = cacheService.<FilesPage>cacheValueOrGet(
                CACHE_NAME_LIST + "-" + this.fileSystemType.name() + "-" + clientCode,
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

        return cacheService.cacheValueOrGet(CACHE_NAME_GET_DETAIL + "-" + this.fileSystemType.name() + "-" + clientCode,
                () -> this.fileSystemDao.getFileDetail(this.fileSystemType, clientCode, path), path);
    }

    public File getAsFile(String path, boolean forceDownload) {

        Path finalPath = Path.of(path.replace("//", "/"));

        if (StringUtil.safeIsBlank(finalPath))
            return null;

        Path filePath = this.createPathInTempFolder(finalPath);

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

        // Map<String, String> map = new HashMap<>();
        // if (!Files.exists(filePath))
        // map.put("create", "true");

        // try (S3TransferManager tm =
        // S3TransferManager.builder().s3Client(s3AsyncClient).build();
        // FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" +
        // filePath.toUri()), map)) {

        // DownloadDirectoryRequest.Builder req = DownloadDirectoryRequest.builder()
        // .bucket(bucketName)
        // .destination(fs.getPath("/"));

        // if (relPath != null && !relPath.isEmpty()) {
        // req.listObjectsV2RequestTransformer(b -> b.prefix(relPath));
        // }

        // tm.downloadDirectory(req.build()).completionFuture().join();

        // return filePath.toFile();

        // } catch (IOException e) {
        // throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Error in
        // downloading directory : " + relPath,
        // e);
        // }

        Path temp = null;
        try (S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build()) {
            temp = Files.createTempDirectory("r2-bulk-");
            // 1) Parallel download to a real directory
            DownloadDirectoryRequest.Builder req = DownloadDirectoryRequest.builder()
                    .bucket(bucketName)
                    .destination(temp);
            if (relPath != null && !relPath.isEmpty()) {
                req.listObjectsV2RequestTransformer(b -> b.prefix(relPath));
            }
            tm.downloadDirectory(req.build()).completionFuture().join(); // wait for all

            // 2) Zip the staged directory (single-writer, safe)
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(filePath))) {
                final Path root = temp;
                Files.walk(root)
                        .filter(Files::isRegularFile)
                        .forEach(p -> {
                            String entryName = root.relativize(p).toString().replace('\\', '/');
                            try {
                                zos.putNextEntry(new ZipEntry(entryName));
                                Files.copy(p, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }

            return filePath.toFile();

        } catch (Exception e) {
            throw new RuntimeException("Error zipping prefix: " + relPath, e);

        } finally {
            // 3) Best-effort cleanup
            if (temp != null) {
                try {
                    Files.walk(temp).sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
                } catch (IOException ignored) {
                }
            }
        }
    }

    private File getDirectoryAsFilev1(String relPath, Path folder, Path filePath) {

        var iterable = s3Client.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucketName)
                        .prefix(relPath)
                        .build())
                .contents();
        int i = 0;

        List<com.modlix.saas.commons2.util.Tuples.Tuple2<Integer, S3Object>> lst = new ArrayList<>();

        for (S3Object s3Object : iterable) {
            s3Client.getObject(
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

        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(path).build());

        // StreamSupport.stream(s3Client.listObjectsV2Paginator(
        // ListObjectsV2Request.builder().bucket(bucketName)
        // .prefix(path)
        // .build())
        // .spliterator(), false)
        // .flatMap(paginator -> paginator.contents().stream())
        // .forEach(e -> {
        // s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(e.key()).build());
        // });

        return this.fileSystemDao.deleteFile(this.fileSystemType, clientCode, finPath);
    }

    public FileDetail createFilesFromMultipartFile(String clientCode, String path, String fileName, MultipartFile fp,
            boolean override) {

        try (InputStream inputStream = fp.getInputStream()) {
            return this.createFileFromInputStream(clientCode, path, fileName, inputStream, fp.getSize(),
                    override, "inline");
        } catch (IOException e) {
            this.messageService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    FilesMessageResourceService.UNKNOWN_ERROR, e.getMessage(), e);
            return null;
        }
    }

    public FileDetail createFileFromFile(String clientCode, String path, String fileName, Path file,
            boolean override) {

        return this.createFileFromFile(clientCode, path, fileName, file, override,
                "inline");
    }

    public FileDetail createFileFromFile(String clientCode, String path, String fileName, Path file,
            boolean override, String contentDisposition) {

        try (InputStream inputStream = Files.newInputStream(file)) {
            long length = Files.size(file);
            return this.createFileFromInputStream(clientCode, path, fileName, inputStream, length,
                    override, contentDisposition);
        } catch (IOException e) {
            this.messageService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    FilesMessageResourceService.UNKNOWN_ERROR, e.getMessage(), e);
            return null;
        }
    }

    public FileDetail createFileFromInputStream(String clientCode, String path, String fileName,
            InputStream inputStream, long length, boolean override, String contentDisposition) {

        String filePath = fileName == null ? path : (path + R2_FILE_SEPARATOR_STRING + fileName);

        boolean exists = this.exists(clientCode, filePath);

        if (exists && !override)
            return this.getFileDetail(clientCode, filePath);

        String key = clientCode;

        if (filePath.startsWith(R2_FILE_SEPARATOR_STRING))
            key += filePath;
        else
            key += R2_FILE_SEPARATOR_STRING + filePath;

        String mimeType = URLConnection.guessContentTypeFromName(filePath);

        try {

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .contentType(mimeType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mimeType)
                            .contentLength(length)
                            .contentDisposition(contentDisposition)
                            .key(key).build(),
                    RequestBody.fromInputStream(inputStream, length));

            Files.deleteIfExists(this.tempFolder.resolve(HashUtil.sha256Hash(key)));
            this.evictCache(clientCode);
            return this.fileSystemDao.createOrUpdateFile(this.fileSystemType, clientCode, filePath,
                    fileName, ULong.valueOf(length),
                    exists && override);

        } catch (IOException e) {
            this.messageService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    FilesMessageResourceService.UNKNOWN_ERROR, e.getMessage(), e);
            return null;
        }
    }

    public void createFileForZipUpload(String clientCode, ULong folderId, String path, Path file,
            boolean override) {

        ULong existingId = this.fileSystemDao.getId(this.fileSystemType, clientCode, folderId, path);

        if (existingId != null && !override)
            return;

        logger.info("Uploading the file : {} : ",
                (clientCode + R2_FILE_SEPARATOR_STRING + path).replace("//", "/"));
        String finalKey = (clientCode + R2_FILE_SEPARATOR_STRING + path).replace("//", "/");

        try {
            long fileLength = Files.size(file);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .contentDisposition("inline")
                            .key(finalKey)
                            .contentLength(fileLength)
                            .build(),
                    file

            );
            Files.deleteIfExists(this.tempFolder.resolve(HashUtil.sha256Hash(finalKey)));

            this.fileSystemDao
                    .createOrUpdateFileForZipUpload(existingId, this.fileSystemType, clientCode, folderId, path,
                            file.getFileName().toString(), ULong.valueOf(fileLength));
        } catch (IOException e) {
            this.messageService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    FilesMessageResourceService.UNKNOWN_ERROR, e.getMessage(), e);
        }
    }

    public <T> void evictCache(String clientCode) {

        this.cacheService.evictAll(CACHE_NAME_EXISTS + "-" + this.fileSystemType.name() + "-" + clientCode);
        this.cacheService.evictAll(CACHE_NAME_GET_DETAIL + "-" + this.fileSystemType.name() + "-" + clientCode);
        this.cacheService.evictAll(CACHE_NAME_LIST + "-" + this.fileSystemType.name() + "-" + clientCode);
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
