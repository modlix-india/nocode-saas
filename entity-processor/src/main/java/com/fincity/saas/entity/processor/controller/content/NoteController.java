package com.fincity.saas.entity.processor.controller.content;

import com.fincity.saas.entity.processor.controller.content.base.BaseContentController;
import com.fincity.saas.entity.processor.dao.content.NoteDAO;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorNotesRecord;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import com.fincity.saas.entity.processor.service.content.NoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/notes")
public class NoteController extends BaseContentController<EntityProcessorNotesRecord, Note, NoteDAO, NoteService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Note>> createFromRequest(@RequestBody NoteRequest noteRequest) {
        return this.service.create(noteRequest).map(ResponseEntity::ok);
    }
}
