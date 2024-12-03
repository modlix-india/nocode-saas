package com.fincity.saas.files.model;

import java.util.List;

public record FilesPage(List<FileDetail> content, int pageNumber, long totalElements) {
}
