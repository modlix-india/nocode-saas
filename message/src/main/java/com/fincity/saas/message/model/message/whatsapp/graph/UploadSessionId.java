package com.fincity.saas.message.model.message.whatsapp.graph;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadSessionId extends BaseId<UploadSessionId> {

    private static final String UPLOAD_PREFIX = "upload:";
    private static final String SIG_SEPARATOR = "?sig=";

    private static final Pattern PATTERN = Pattern.compile("^upload:([^?]+)\\?sig=(.+)$");

    @JsonIgnore
    private String upload;

    @JsonIgnore
    private String sig;

    public static UploadSessionId of(String id) {
        return new UploadSessionId().setId(id);
    }

    @Override
    public UploadSessionId setId(String id) {
        if (id == null || id.isBlank()) {
            clearAll();
            return this;
        }

        Matcher m = PATTERN.matcher(id);
        if (!m.matches()) {
            clearAll();
            return this;
        }

        this.upload = m.group(1);
        this.sig = m.group(2);

        // Canonicalize
        super.setId(UPLOAD_PREFIX + this.upload + SIG_SEPARATOR + this.sig);
        return this;
    }

    @JsonIgnore
    public boolean isNull() {
        return this.sig == null || this.upload == null || super.getId() == null;
    }

    private void clearAll() {
        this.upload = null;
        this.sig = null;
        super.setId(null);
    }
}
