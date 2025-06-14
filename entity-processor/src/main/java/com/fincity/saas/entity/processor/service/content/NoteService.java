package com.fincity.saas.entity.processor.service.content;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.content.NoteDAO;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorNotesRecord;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.content.base.BaseContentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class NoteService extends BaseContentService<NoteRequest, EntityProcessorNotesRecord, Note, NoteDAO> {

    private static final String NOTE_CACHE = "note";

    @Override
    protected String getCacheName() {
        return NOTE_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.NOTE;
    }

    @Override
    protected Mono<Note> createContent(NoteRequest contentRequest) {

        if (contentRequest.getContent() == null
                || contentRequest.getContent().trim().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.CONTENT_MISSING,
                    this.getEntityName());

        return Mono.just(new Note().of(contentRequest));
    }
}
