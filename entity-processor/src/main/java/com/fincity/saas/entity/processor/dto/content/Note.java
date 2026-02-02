package com.fincity.saas.entity.processor.dto.content;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
@IgnoreGeneration
public class Note extends BaseContentDto<Note> {

    @Serial
    private static final long serialVersionUID = 4656579497586549236L;

    private FileDetail attachmentFileDetail;

    public Note() {
        super();
    }

    public Note(Note note) {
        super(note);
        this.attachmentFileDetail = CloneUtil.cloneObject(note.attachmentFileDetail);
    }

    public static Note of(NoteRequest noteRequest) {
        Note note = (Note) new Note()
                .setContent(noteRequest.getContent())
                .setHasAttachment(noteRequest.getHasAttachment())
                .setContentEntitySeries(noteRequest.getContentEntitySeries());

        note.setAttachmentFileDetail(noteRequest.getAttachmentFileDetail());

        return switch (note.getContentEntitySeries()) {
            case OWNER -> note.setOwnerId(noteRequest.getOwnerId().getULongId());
            case TICKET -> note.setTicketId(noteRequest.getTicketId().getULongId());
            case USER -> note.setUserId(noteRequest.getUserId());
        };
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.NOTE;
    }
}
