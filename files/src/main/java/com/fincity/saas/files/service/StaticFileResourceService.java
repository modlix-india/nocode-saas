package com.fincity.saas.files.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.files.dao.FileSystemDao;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.jooq.enums.FilesFileSystemType;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Service
public class StaticFileResourceService extends AbstractFilesResourceService {

	@Value("${files.resources.bucketPrefix:}")
	private String bucketPrefix;

	private FileSystemService fileSystemService;

	private final FileSystemDao fileSystemDao;
	private final CacheService cacheService;
	private final S3AsyncClient s3Client;

	public StaticFileResourceService(
			FilesAccessPathService filesAccessPathService, FilesMessageResourceService msgService,
			FileSystemDao fileSystemDao, CacheService cacheService, S3AsyncClient s3Client) {
		super(filesAccessPathService, msgService);
		this.fileSystemDao = fileSystemDao;
		this.cacheService = cacheService;
		this.s3Client = s3Client;
	}

	@Override
	@PostConstruct
	public void initialize() {
		super.initialize();
		String bucketName = this.bucketPrefix + "-" + this.getResourceType().toLowerCase();

		this.fileSystemService = new FileSystemService(this.fileSystemDao, this.cacheService, bucketName,
				this.s3Client, FilesFileSystemType.STATIC);
	}

	@Override
	public FileSystemService getFSService() {
		return this.fileSystemService;
	}

	@Override
	public String getResourceType() {
		return FilesAccessPathResourceType.STATIC.name();
	}

}
