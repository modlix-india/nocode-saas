package com.fincity.saas.entity.processor.service.content;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.content.NoteDAO;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorNotesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.content.base.BaseContentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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

    public Mono<Note> create(NoteRequest noteRequest) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.updateIdentities(access, noteRequest),
                (access, uRequest) -> this.createContent(uRequest),
                (access, uRequest, content) -> content.isTicketContent()
                        ? super.createTicketContent(access, content)
                        : super.createOwnerContent(access, content));
    }

    public Mono<Note> createInternal(ProcessorAccess access, NoteRequest noteRequest) {
        return FlatMapUtil.flatMapMono(
                () -> this.updateIdentities(access, noteRequest),
                this::createContent,
                (uRequest, content) -> content.isTicketContent()
                        ? super.createTicketContent(access, content)
                        : super.createOwnerContent(access, content));
    }

    private Mono<NoteRequest> updateIdentities(ProcessorAccess access, NoteRequest noteRequest) {
        return FlatMapUtil.flatMapMono(
                () -> noteRequest.getTicketId() != null
                        ? this.checkTicket(access, noteRequest.getTicketId())
                        : Mono.just(Identity.ofNull()),
                ticketId -> noteRequest.getOwnerId() != null
                        ? this.checkOwner(access, noteRequest.getOwnerId(), ticketId)
                        : Mono.just(Identity.ofNull()),
                (ticketId, ownerId) ->
                        Mono.just(noteRequest.setTicketId(ticketId).setOwnerId(ownerId)));
    }

    private Mono<Note> createContent(NoteRequest noteRequest) {
        if (!noteRequest.hasContent())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.CONTENT_MISSING,
                    this.getEntityName());

        return Mono.just(Note.of(noteRequest));
    }
}
