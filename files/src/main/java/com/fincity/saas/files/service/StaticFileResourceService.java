package com.fincity.saas.files.service;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class StaticFileResourceService extends AbstractFilesResourceService {

	@Value("${files.resources.location.static}")
	private String location;
	
	private String staticResourceLocation;
	
	@PostConstruct
	private void initialize() {
		this.staticResourceLocation = location.replace("/", "\\\\");
	}

	@Override
	public String getBaseLocation() {
		return this.staticResourceLocation;
	}
	
	@Override
	public String getResourceType() {
		return "Static";
	}

	@Override
	public Mono<Path> resolveFileToRead(String filePath) {
		
		return Mono.just(Paths.get(this.staticResourceLocation, filePath));
	}

	
}
