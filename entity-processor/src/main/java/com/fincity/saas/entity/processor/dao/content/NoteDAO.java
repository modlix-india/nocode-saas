package com.fincity.saas.entity.processor.dao.content;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_NOTES;

import org.springframework.stereotype.Component;

import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorNotesRecord;

@Component
public class NoteDAO extends BaseContentDAO<EntityProcessorNotesRecord, Note> {
    protected NoteDAO() {
        super(Note.class, ENTITY_PROCESSOR_NOTES, ENTITY_PROCESSOR_NOTES.ID);
    }
}
