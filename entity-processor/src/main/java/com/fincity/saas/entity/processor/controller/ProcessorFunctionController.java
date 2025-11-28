package com.fincity.saas.entity.processor.controller;

import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.saas.entity.processor.service.ProcessorFunctionService;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.RestController;
import lombok.Data;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/core/function")
public class ProcessorFunctionController {

    private static final String RESULT_KEY = "result";

    private final ProcessorFunctionService processorFunctionService;

    public ProcessorFunctionController(@Lazy ProcessorFunctionService processorFunctionService) {
        this.processorFunctionService = processorFunctionService;
    }

    @PostMapping("/{namespace}/{name}")
    public Mono<ResponseEntity<JsonElement>> executeFunction(
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody(required = false) Map<String, JsonElement> arguments) {
        return processorFunctionService
                .execute(namespace, name, arguments != null ? arguments : Map.of())
                .map(this::extractResult)
                .map(result -> result != null ? ResponseEntity.ok(result) : createNoContentResponse())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/execute")
    public Mono<ResponseEntity<JsonElement>> executeFunction(@RequestBody FunctionExecutionRequest request) {
        return processorFunctionService
                .execute(
                        request.getNamespace(),
                        request.getName(),
                        request.getArguments() != null ? request.getArguments() : Map.of())
                .map(this::extractResult)
                .map(result -> result != null ? ResponseEntity.ok(result) : createNoContentResponse())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * List all available functions, optionally filtered by name.
     *
     * @param filter Optional filter string to search function names (case-insensitive)
     * @return List of function full names (namespace.name)
     */
    @GetMapping("/list")
    public Mono<ResponseEntity<List<String>>> listFunctions(@RequestParam(required = false) String filter) {
        return processorFunctionService
                .getFunctionRepository()
                .filter(filter != null ? filter : "")
                .collectList()
                .map(ResponseEntity::ok);
    }

    private ResponseEntity<JsonElement> createNoContentResponse() {
        return ResponseEntity.noContent().build();
    }

    private JsonElement extractResult(FunctionOutput functionOutput) {
        EventResult eventResult;
        while ((eventResult = functionOutput.next()) != null) {
            if (Event.OUTPUT.equals(eventResult.getName())) {
                Map<String, JsonElement> results = eventResult.getResult();
                return results != null ? results.get(RESULT_KEY) : null;
            }
        }
        return null;
    }


	@Data
    public static class FunctionExecutionRequest {
        private String namespace;
        private String name;
        private Map<String, JsonElement> arguments;

    }
}

