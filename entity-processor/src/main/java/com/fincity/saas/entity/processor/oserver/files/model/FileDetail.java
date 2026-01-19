package com.fincity.saas.entity.processor.oserver.files.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1234567890123456789L;

    private ULong id;
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

    public FileDetail() {}

    public FileDetail(FileDetail fileDetail) {
        this.id = fileDetail.id;
        this.name = fileDetail.name;
        this.isDirectory = fileDetail.isDirectory;
        this.size = fileDetail.size;
        this.filePath = fileDetail.filePath;
        this.url = fileDetail.url;
        this.createdDate = fileDetail.createdDate;
        this.lastModifiedTime = fileDetail.lastModifiedTime;
        this.type = fileDetail.type;
        this.fileName = fileDetail.fileName;
    }
}
