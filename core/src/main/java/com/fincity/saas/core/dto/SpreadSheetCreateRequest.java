package com.fincity.saas.core.dto;

import com.fincity.saas.commons.util.DataFileType;
import com.google.gson.JsonElement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpreadSheetCreateRequest {

    private String name;
    private Object data;
    private String[] headers;
    private DataFileType type = DataFileType.CSV;

    private boolean downloadable = true;
    private boolean skipHeader = false;
}