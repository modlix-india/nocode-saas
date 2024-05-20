package com.fincity.saas.files.service;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;

@Service
public class StaticFileResourceService extends AbstractFilesResourceService {

	public StaticFileResourceService(
			FilesAccessPathService filesAccessPathService, FilesMessageResourceService msgService) {
		super(filesAccessPathService, msgService);
	}

	@Value("${files.resources.location.static}")
	private String location;

	private String staticResourceLocation;

	@PostConstruct
	private void initializeStatic() {
		this.staticResourceLocation = location;
	}

	@Override
	public String getBaseLocation() {
		return this.staticResourceLocation;
	}

	@Override
	public FilesAccessPathResourceType getResourceType() {
		return FilesAccessPathResourceType.STATIC;
	}
}
