package com.fincity.sass.worker.model.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.sass.worker.dto.Task;
import com.fincity.sass.worker.model.common.FunctionExecutionSpec;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Task model for function execution jobs. Extends Task and adds function execution spec
 * handling. Used for API and business logic - function params are stored in jobData.
 * <p>
 * API can send functionName, functionNamespace, functionParams - call {@link #prepareForPersistence()}
 * before create/update to merge into jobData.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FunctionExecutionTask extends Task {

    private static final Gson GSON = new Gson();

    /** API input: when set, merged into jobData via prepareForPersistence(). */
    @JsonProperty("functionName")
    private transient String functionName;
    @JsonProperty("functionNamespace")
    private transient String functionNamespace;
    @JsonProperty("functionParams")
    private transient String functionParams;

    /**
     * Merge API input (functionName, functionNamespace, functionParams) into jobData.
     * Call before create/update when using function fields from API.
     */
    public FunctionExecutionTask prepareForPersistence() {
        String fn = functionName != null ? functionName : (getFunctionExecutionSpec() != null ? getFunctionExecutionSpec().getName() : null);
        String fns = functionNamespace != null ? functionNamespace : (getFunctionExecutionSpec() != null ? getFunctionExecutionSpec().getNamespace() : null);
        Map<String, JsonElement> fp = functionParams != null ? parseParams(functionParams) : (getFunctionExecutionSpec() != null ? getFunctionExecutionSpec().getParams() : null);
        if (fn != null || fns != null) {
            setFunctionExecutionSpec(new FunctionExecutionSpec(fns, fn, fp != null ? fp : Map.of()));
        }
        return this;
    }

    /**
     * Get function execution spec from jobData. Returns null if not a function execution task.
     */
    public FunctionExecutionSpec getFunctionExecutionSpec() {
        return FunctionExecutionSpec.fromJobData(getJobData());
    }

    /**
     * Set function execution spec into jobData. Merges with existing jobData.
     */
    public FunctionExecutionTask setFunctionExecutionSpec(FunctionExecutionSpec spec) {
        if (spec == null) return this;
        Map<String, Object> jobData = getJobData() != null ? new HashMap<>(getJobData()) : new HashMap<>();
        spec.mergeInto(jobData);
        setJobData(jobData);
        return this;
    }

    /**
     * Convenience: set function spec from individual fields.
     */
    public FunctionExecutionTask setFunctionExecutionSpec(String namespace, String name, Map<String, JsonElement> params) {
        return setFunctionExecutionSpec(new FunctionExecutionSpec(namespace, name, params != null ? params : Map.of()));
    }

    /**
     * Returns true if this task has function execution spec.
     */
    public boolean isFunctionExecutionTask() {
        return getFunctionExecutionSpec() != null && getFunctionExecutionSpec().hasFunctionSpec();
    }

    private static Map<String, JsonElement> parseParams(String paramsStr) {
        if (paramsStr == null || paramsStr.isBlank()) return Collections.emptyMap();
        try {
            return GSON.fromJson(paramsStr, new TypeToken<Map<String, JsonElement>>() {}.getType());
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
