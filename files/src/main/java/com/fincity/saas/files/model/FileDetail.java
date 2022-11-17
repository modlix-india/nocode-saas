package com.fincity.saas.files.model;

import java.nio.file.attribute.FileTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.files.util.FileExtensionUtil;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FileDetail {

	private String name;
	private boolean isDirectory;
	private long size;
	private String fullFileName;
	private long createdDate;
	private long lastModifiedTime;
	private long lastAccessTime;

	@JsonIgnore
	private FileTime modifiedTime;

	@JsonProperty("type")
	public String getFileType() {
		
		return FileExtensionUtil.get(name);
	}
	
	@JsonProperty("fileName")
	public String getFileName() {
		
		if (name == null || name.isBlank())
			return "";
		
		int ind = name.lastIndexOf('.');
		if (ind <= 0)
			return name;
		
		return name.substring(0, ind);
	}

	@JsonProperty("isCompressedFile")
	public boolean isCompressedFile() {

		if (this.name == null || this.name.isBlank())
			return false;

		String lowerName = this.name.toLowerCase();

		return lowerName.endsWith(".zip") || lowerName.endsWith(".gz");
	}
}
