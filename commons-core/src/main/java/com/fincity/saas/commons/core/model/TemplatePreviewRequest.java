package com.fincity.saas.commons.core.model;

import com.fincity.saas.commons.core.document.Template;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class TemplatePreviewRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // The (possibly unsaved) template being edited.
    private Template template;
    // Data to render against; falls back to template.sampleData when null/empty.
    private Map<String, Object> data;
    // Optional language override; blank falls back to the template's language resolution.
    private String language;
}
