package com.fincity.sass.worker.service.execution;

import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.feign.IFeignCoreService;
import com.fincity.sass.worker.model.common.FunctionExecutionSpec;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CoreFunctionExecutionService extends AbstractExecutionService {

    private final IFeignCoreService feignCoreService;
    private final Gson gson;

    public CoreFunctionExecutionService(IFeignCoreService feignCoreService, Gson gson) {
        this.feignCoreService = feignCoreService;
        this.gson = gson;
    }

    @Override
    public String execute(Task task) {
        FunctionExecutionSpec spec = FunctionExecutionSpec.fromJobData(task.getJobData());
        if (spec == null || !spec.hasFunctionSpec())
            return "No function specified for execution";

        Map<String, JsonElement> params = spec.getParams() != null ? spec.getParams() : Map.of();
        return execute(task, spec, params);
    }

    private String execute(Task task, FunctionExecutionSpec spec, Map<String, JsonElement> params) {
        String clientCode = task.getClientCode();
        String appCode = task.getAppCode();
        if (appCode == null || appCode.isBlank() || clientCode == null || clientCode.isBlank())
            return "Error: task must have appCode and clientCode";

        logger.info(
                "Executing core function for taskId={}, namespace={}, name={}, appCode={}, clientCode={}",
                task.getId(),
                spec.getNamespace(),
                spec.getName(),
                appCode,
                clientCode);
        try {
            String forwardedHost = "worker";
            String forwardedPort = "0";
            String jsonParams = gson.toJson(params != null ? params : Map.of());
            return runWithTimeout(() -> feignCoreService.executeWith(
                    forwardedHost,
                    forwardedPort,
                    appCode,
                    clientCode,
                    spec.getNamespace(),
                    spec.getName(),
                    jsonParams));
        } catch (Exception e) {
            logger.error(
                    "Core function execution failed for taskId={}, namespace={}, name={}: {}",
                    task.getId(),
                    spec.getNamespace(),
                    spec.getName(),
                    e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
