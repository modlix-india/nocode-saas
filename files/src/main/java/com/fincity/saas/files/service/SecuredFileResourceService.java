package com.fincity.saas.files.service;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.model.FileDetail;

import reactor.core.publisher.Mono;

@Service
public class SecuredFileResourceService extends AbstractFilesResourceService {

	@Value("${files.resources.location.secured}")
	private String location;

	private String securedResourceLocation;

	@PostConstruct
	private void initializeStatic() {
		this.securedResourceLocation = location;
	}

	@Override
	public String getBaseLocation() {
		return this.securedResourceLocation;
	}

	@Override
	public FilesAccessPathResourceType getResourceType() {
		return FilesAccessPathResourceType.SECURED;
	}

	@Override
	protected Mono<Boolean> checkReadAccessWithClientCode(String resourcePath) {

		int index = resourcePath.indexOf('/', 1);
		String clientCode = null;
		if (index != -1) {

			clientCode = resourcePath.substring(1, index);
			resourcePath = resourcePath.substring(index);
		} else {

			clientCode = resourcePath;
			resourcePath = "";
		}

		return this.fileAccessService.hasReadAccess(resourcePath, clientCode, FilesAccessPathResourceType.SECURED);
	}

	@Override
	public Mono<FileDetail> create(String clientCode, String uri, FilePart fp, String fileName, Boolean override) {

		if (override == null)
			override = false;

		return super.create(clientCode, uri, fp, fileName, override);
	}
}
