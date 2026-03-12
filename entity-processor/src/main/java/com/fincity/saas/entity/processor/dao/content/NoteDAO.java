package com.fincity.saas.entity.processor.dao.content;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_NOTES;

import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorNotesRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class NoteDAO extends BaseContentDAO<EntityProcessorNotesRecord, Note> {

    protected NoteDAO() {
        super(Note.class, ENTITY_PROCESSOR_NOTES, ENTITY_PROCESSOR_NOTES.ID);
    }

    public Flux<Note> readAllByTicketId(ULong ticketId) {
        return Flux.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_NOTES)
                        .where(ENTITY_PROCESSOR_NOTES.TICKET_ID.eq(ticketId)))
                .map(e -> e.into(Note.class));
    }
}
