package com.fincity.saas.files.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.util.FileSystemUtils;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.files.dao.FileSystemDao;
import com.fincity.saas.files.jooq.enums.FilesFileSystemType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class FileSystemServiceTest {
	private static final String TEST_BUCKET = "FileSystemServiceTest-bucket";
	private FileSystemService fileSystemService;
	private MockS3AsyncClient mockS3Client;

	@BeforeEach
	void setUp() {
		FileSystemDao fileSystemDao = Mockito.mock(FileSystemDao.class);
		CacheService cacheService = Mockito.mock(CacheService.class);
		mockS3Client = new MockS3AsyncClient(TEST_BUCKET);
		FilesFileSystemType fileSystemType = FilesFileSystemType.SECURED;
		fileSystemService = new FileSystemService(fileSystemDao, cacheService, TEST_BUCKET, mockS3Client, fileSystemType);
	}

	@AfterEach
	void tearDown() {
		mockS3Client.close();
		FileSystemUtils.deleteRecursively(fileSystemService.getTempFolder().toFile());
	}


	@Test
	void testGetAsFile() {
		String path = "test/path/to/file.txt";
		boolean forceDownload = false;

		CompletableFuture<PutObjectResponse> putFuture;

		putFuture = mockS3Client.putObject(
				builder -> builder.key(path),
				AsyncRequestBody.fromPublisher(Flux.just(ByteBuffer.wrap("Test content".getBytes())))
		);

		putFuture.join();

		Mono<File> result = fileSystemService.getAsFile(path, forceDownload).switchIfEmpty(Mono.error(new AssertionError("not found")));

		File file = result.block();

		assertNotNull(file);

		assertTrue(file.exists());

		assertTrue(file.isFile());
	}
}
