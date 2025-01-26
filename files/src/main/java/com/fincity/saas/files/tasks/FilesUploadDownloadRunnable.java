package com.fincity.saas.files.tasks;

import com.fincity.saas.files.dto.FilesUploadDownloadDTO;
import com.fincity.saas.files.service.FilesUploadDownloadService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class FilesUploadDownloadRunnable implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(FilesUploadDownloadRunnable.class);

    private final FilesUploadDownloadDTO fud;
    private final FilesUploadDownloadService fudService;

    private final Mono<?> task;
    private final Mono<?> postRunTask;

    public FilesUploadDownloadRunnable(Mono<?> task, FilesUploadDownloadDTO fud, FilesUploadDownloadService fudService, Mono<?> postRunTask) {
        this.fud = fud;
        this.fudService = fudService;
        this.task = task;
        this.postRunTask = postRunTask;
    }

    public FilesUploadDownloadRunnable(Mono<?> task, FilesUploadDownloadDTO fud, FilesUploadDownloadService fudService) {
        this(task, fud, fudService, null);
    }

    @Override
    public void run() {

        this.task
                .flatMap(x -> this.postRunTask == null ? Mono.just(x) : this.postRunTask.map(y -> x))
                .flatMap(e -> fudService.updateDone(fud.getId()))
                .onErrorResume(e -> {
                    logger.error("Unable to perform task : {}", this.fud, e);
                    return fudService.updateFailed(e, fud.getId());
                })
                .subscribe();
    }
}
