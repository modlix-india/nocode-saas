package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.dto.Tag;
import com.fincity.saas.entity.processor.service.TagService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/tags")
public class TagController {

    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ResponseEntity<List<Tag>>> getAvailableTags(
            @RequestParam(defaultValue = "true") boolean onlyActive) {
        return this.service.getAvailableTags(onlyActive).map(ResponseEntity::ok);
    }

    @GetMapping("/mapped")
    public Mono<ResponseEntity<Map<String, Tag>>> getAvailableTagsMap(
            @RequestParam(defaultValue = "true") boolean onlyActive) {
        return this.service.getAvailableTagsMap(onlyActive).map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<List<Tag>>> saveAllTags(@RequestBody List<Tag> tags) {
        return this.service.saveAllTags(tags).map(ResponseEntity::ok);
    }
}
