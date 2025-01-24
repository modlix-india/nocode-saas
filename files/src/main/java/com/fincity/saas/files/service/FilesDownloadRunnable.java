package com.fincity.saas.files.service;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.fincity.saas.files.dto.FilesUploadDownloadDTO;

public class FilesDownloadRunnable implements Runnable {

    private final FileSystemService fsService;
    private final FilesUploadDownloadDTO fud;
    private final FilesUploadDownloadService fudService;

    public FilesDownloadRunnable(FileSystemService fsService, FilesUploadDownloadDTO fud,
            FilesUploadDownloadService fudService) {
        this.fsService = fsService;
        this.fud = fud;
        this.fudService = fudService;
    }

    @Override
    public void run() {

        this.fsService
                .getDirectoryAsArchive(
                        this.fud.getClientCode() + FileSystemService.R2_FILE_SEPARATOR_STRING + this.fud.getPath())
                .flatMap(archive -> {

                    Path path = Paths.get(archive.getAbsolutePath());

                    int lastIndex = this.fud.getPath().lastIndexOf('/');
                    String name = this.fud.getPath().substring(lastIndex + 1);

                    lastIndex = this.fud.getCdnUrl().lastIndexOf('/');
                    String cdnFileName = this.fud.getCdnUrl().substring(lastIndex + 1);

                    String cdnPath = fud.getCdnUrl().substring(0, lastIndex);

                    return this.fsService.createFileFromFile(this.fud.getClientCode(),
                            cdnPath, cdnFileName, path, true,
                            "attachment; filename=\"" + name + ".zip\"")
                            .flatMap(fd -> fudService.updateDone(fud.getId()));
                }).subscribe();
    }
}
