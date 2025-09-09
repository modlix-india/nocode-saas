package com.fincity.saas.message.oserver.files.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1234567890123456789L;

    private String name;

    @JsonProperty("directory")
    private Boolean isDirectory;

    private Long size;
    private String filePath;
    private String url;
    private Long createdDate;
    private Long lastModifiedTime;
    private String type;
    private String fileName;

    @JsonProperty("isCompressedFile")
    public Boolean isCompressedFile() {

        if (this.type == null) return false;

        return this.type.endsWith(".zip") || this.type.endsWith(".gz");
    }
}
