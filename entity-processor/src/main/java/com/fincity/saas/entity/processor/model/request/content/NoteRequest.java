package com.fincity.saas.entity.processor.model.request.content;

import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class NoteRequest extends BaseContentRequest<NoteRequest> {

    @Serial
    private static final long serialVersionUID = 3413802729099695321L;

    private FileDetail attachmentFileDetail;
}
