package com.fincity.saas.entity.processor.controller.content;

import com.fincity.saas.entity.processor.controller.content.base.BaseContentController;
import com.fincity.saas.entity.processor.dao.content.NoteDAO;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorNotesRecord;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import com.fincity.saas.entity.processor.service.content.NoteService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/notes")
public class NoteController
        extends BaseContentController<NoteRequest, EntityProcessorNotesRecord, Note, NoteDAO, NoteService> {}
