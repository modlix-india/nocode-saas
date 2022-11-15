package com.fincity.saas.files.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StaticFileResourceService extends AbstractFilesResourceService {

	@Value("${files.resources.location.static}")
	private String staticResourceLocation;

	@Override
	public String getBaseLocation() {
		return this.staticResourceLocation;
	}
	
	@Override
	public String getResourceType() {
		return "Static";
	}
}
