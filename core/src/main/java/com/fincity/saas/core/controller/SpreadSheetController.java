package com.fincity.saas.core.controller;

import com.fincity.saas.commons.file.DataFileWriter;
import com.fincity.saas.core.dto.SpreadSheetCreateRequest;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.springframework.data.util.StreamUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/core/spreadsheet")
public class SpreadSheetController {

    private final Gson gson;

    public SpreadSheetController(Gson gson) {
        this.gson = gson;
    }

    @PostMapping()
    public Mono<Void> createSpreadSheet(@RequestBody SpreadSheetCreateRequest request, ServerHttpResponse response) {

        ByteArrayOutputStream bAOS = new ByteArrayOutputStream();

        JsonElement jsonElement = gson.toJsonTree(request.getData());

        List<String> headers = null;
        List<Map<String, Object>> rows = new ArrayList<>();

        if (request.getHeaders() != null && request.getHeaders().length > 0) {
            headers = List.of(request.getHeaders());
        }

        if (jsonElement.isJsonArray() && !jsonElement.getAsJsonArray().isEmpty()) {
            JsonElement firstElement = jsonElement.getAsJsonArray().get(0);
            if (firstElement.isJsonArray()) {
                int i = request.isSkipHeader() ? 1 : 0;
                request.setSkipHeader(true);
                headers = new ArrayList<>();
                for (int j = 0; j < firstElement.getAsJsonArray().size(); j++) headers.add("" + j);

                StreamUtils.createStreamFromIterator(jsonElement.getAsJsonArray().iterator()).skip(i).map(e -> {
                    Map<String, Object> row = new HashMap<>();
                    for (int j = 0; j < e.getAsJsonArray().size(); j++) {
                        row.put("" + j, e.getAsJsonArray().get(j).getAsString());
                    }
                    return row;
                }).forEach(rows::add);
            } else if (firstElement.isJsonObject()) {
                boolean noHeaders = headers == null || headers.isEmpty();
                if (noHeaders) {
                    headers = new ArrayList<>();
                    for (Map.Entry<String, JsonElement> entry : firstElement.getAsJsonObject().entrySet()) {
                        headers.add(entry.getKey());
                    }
                }

                StreamUtils.createStreamFromIterator(jsonElement.getAsJsonArray().iterator()).map(e -> {
                    Map<String, Object> row = new HashMap<>();
                    e.getAsJsonObject().entrySet().forEach(entry -> row.put(entry.getKey(), entry.getValue().getAsString()));
                    return row;
                }).forEach(rows::add);
            }
        } else if (jsonElement.isJsonObject()) {
            boolean noHeaders = headers == null || headers.isEmpty();
            if (noHeaders) headers = new ArrayList<>();
            Map<String, Object> row = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : jsonElement.getAsJsonObject().entrySet()) {
                if (noHeaders) {
                    headers.add(entry.getKey());
                }
                row.put(entry.getKey(), entry.getValue().getAsString());
            }
            rows.add(row);
        }

        try {
            DataFileWriter writer = new DataFileWriter(headers, request.getType(), bAOS, !request.isSkipHeader());

            for (Map<String, Object> row : rows) {
                writer.write(row);
            }
        } catch (Exception e) {
            return Mono.error(e);
        }

        byte[] bytes = bAOS.toByteArray();
        ZeroCopyHttpOutputMessage zeroCopyResponse = (ZeroCopyHttpOutputMessage) response;
        response.getHeaders().setContentLength(bytes.length);
        response.getHeaders().setContentType(MediaType.valueOf(request.getType().getMimeType()));

        response.getHeaders().setContentDisposition(ContentDisposition
                .builder(request.isDownloadable() ? "attachment" : "inline")
                .filename(request.getName() == null ? "spreadSheet." + request.getType().name().toLowerCase() : request.getName()).build());

        return zeroCopyResponse.writeWith(Mono.just(response.bufferFactory()
                .wrap(bytes)));
    }
}