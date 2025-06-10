package com.fincity.saas.entity.processor.service.content;

import org.springframework.stereotype.Service;

import com.fincity.saas.entity.processor.dao.content.NoteDAO;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorNotesRecord;
import com.fincity.saas.entity.processor.service.content.base.BaseContentService;

@Service
public class NoteService extends BaseContentService<EntityProcessorNotesRecord, Note, NoteDAO> {

    private static final String NOTE_CACHE = "note";

    @Override
    protected String getCacheName() {
        return NOTE_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.NOTE;
    }
}
