package com.fincity.sass.worker.service;

import com.modlix.saas.commons2.configuration.service.AbstractMessageService;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import org.springframework.stereotype.Service;

@Service
public class WorkerMessageResourceService extends AbstractMessageService {

    public static final String OBJECT_NOT_FOUND_TO_UPDATE = "object_not_found_to_update";
    public static final String PARAMS_NOT_FOUND = "params_not_found";
    public static final String FORBIDDEN_CREATE = "forbidden_create";
    public static final String FORBIDDEN_UPDATE = "forbidden_update";
    public static final String FORBIDDEN_PERMISSION = "forbidden_permission";
    public static final String SCHEDULER_NOT_FOUND = "scheduler_not_found";
    public static final String TASK_NOT_FOUND = "task_not_found";
    public static final String FAILED_TO_INITIALIZE_SCHEDULER = "failed_to_initialize_scheduler";
    public static final String FAILED_TO_CANCEL_TASK = "failed_to_cancel_task";
    public static final String FAILED_TO_PAUSE_TASK = "failed_to_pause_task";
    public static final String FAILED_TO_RESUME_TASK = "failed_to_resume_task";
    public static final String FAILED_TO_START_SCHEDULER = "failed_to_start_scheduler";
    public static final String FAILED_TO_PAUSE_SCHEDULER = "failed_to_pause_scheduler";
    public static final String FAILED_TO_SHUTDOWN_SCHEDULER = "failed_to_shutdown_scheduler";
    public static final String TASK_DELETION_NOT_ALLOWED = "task_deletion_not_allowed";
    public static final String SCHEDULER_DELETION_NOT_ALLOWED = "scheduler_deletion_not_allowed";
    public static final String TASK_ID_NOT_FOUND = "task_id_not_found";

    protected WorkerMessageResourceService() {
        super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
    }
}
