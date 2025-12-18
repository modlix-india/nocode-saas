package com.fincity.saas.entity.processor.service.content;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.AbstractProcessorFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.content.NoteDAO;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorNotesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.content.base.BaseContentService;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class NoteService extends BaseContentService<EntityProcessorNotesRecord, Note, NoteDAO>
        implements IRepositoryProvider {

    private static final String NOTE_CACHE = "note";

    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final Gson gson;

    private final ClassSchema classSchema = ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    @Autowired
    @Lazy
    private NoteService self;

    public NoteService(Gson gson) {
        this.gson = gson;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(super.getCommonFunctions("Note", Note.class, gson));

        String noteSchemaRef = classSchema.getNamespaceForClass(Note.class) + "." + Note.class.getSimpleName();
        ClassSchema.ArgSpec<NoteRequest> noteRequest = ClassSchema.ArgSpec.ofRef("noteRequest", NoteRequest.class);

        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Note",
                "CreateRequest",
                noteRequest,
                "created",
                Schema.ofRef(noteSchemaRef),
                gson,
                self::createRequest));
    }

    @Override
    protected String getCacheName() {
        return NOTE_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.NOTE;
    }

    public Mono<Note> createRequest(NoteRequest noteRequest) {
        return FlatMapUtil.flatMapMono(super::hasAccess, access -> this.createRequest(access, noteRequest))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NoteService.createRequest"));
    }

    public Mono<Note> createRequest(ProcessorAccess access, NoteRequest noteRequest) {
        return FlatMapUtil.flatMapMono(
                        () -> super.updateBaseIdentities(access, noteRequest),
                        this::createContent,
                        (uRequest, content) -> super.createContent(access, content))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "NoteService.createRequest[ProcessorAccess, NoteRequest]"));
    }

    private Mono<Note> createContent(NoteRequest noteRequest) {
        if (!noteRequest.hasContent())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.CONTENT_MISSING,
                    this.getEntityName());

        return Mono.just(Note.of(noteRequest))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NoteService.createContent"));
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(Note.class, classSchema);
    }
}
