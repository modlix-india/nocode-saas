package com.fincity.saas.files.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileSystemUtils;

import com.fincity.saas.commons.exeception.GenericException;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class MockS3AsyncClient implements S3AsyncClient {

	private static final Logger logger = LoggerFactory.getLogger(MockS3AsyncClient.class);

	private final Path tempFolder;

	public MockS3AsyncClient(String bucketName) {
		try {
			this.tempFolder = Files.createTempDirectory("download-" + bucketName);
		} catch (IOException e) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Error in creating temporary folder for : " + bucketName, e);
		}
	}

	@Override
	public String serviceName() {
		return "MockS3AsyncClient";
	}

	@Override
	public void close() {
		logger.info("Closing MockS3AsyncClient");
		logger.info("Cleaning up temp folder {}", tempFolder);
		FileSystemUtils.deleteRecursively(tempFolder.toFile());
	}

	@Override
	public <T> CompletableFuture<T> getObject(GetObjectRequest getObjectRequest, AsyncResponseTransformer<GetObjectResponse, T> asyncResponseTransformer) {
		Path filePath = tempFolder.resolve(getObjectRequest.key());

		logger.info("Attempting to get object: {}, resolved to path: {}", getObjectRequest.key(), filePath);

		if (!Files.exists(filePath)) {
			CompletableFuture<T> future = new CompletableFuture<>();
			future.completeExceptionally(new GenericException(HttpStatus.NOT_FOUND,
					"File not found: " + getObjectRequest.key()));
			return future;
		}

		CompletableFuture<T> future = asyncResponseTransformer.prepare();

		try {
			long contentLength = Files.size(filePath);
			GetObjectResponse response = GetObjectResponse.builder()
					.contentLength(contentLength)
					.build();

			asyncResponseTransformer.onResponse(response);

			SdkPublisher<ByteBuffer> publisher = subscriber -> {
				try {
					byte[] content = Files.readAllBytes(filePath);
					ByteBuffer buffer = ByteBuffer.wrap(content);

					subscriber.onSubscribe(new Subscription() {
						private boolean cancelled = false;

						@Override
						public void request(long n) {
							if (cancelled || n <= 0) return;

							try {
								if (buffer.hasRemaining()) {
									subscriber.onNext(buffer);
								}
								subscriber.onComplete();
							} catch (Exception e) {
								subscriber.onError(e);
								if (!future.isDone()) {
									future.completeExceptionally(e);
								}
							}
						}

						@Override
						public void cancel() {
							cancelled = true;
						}
					});
				} catch (IOException e) {
					subscriber.onError(e);
					if (!future.isDone()) {
						future.completeExceptionally(e);
					}
				}
			};

			asyncResponseTransformer.onStream(publisher);

			return future;
		} catch (Exception e) {
			asyncResponseTransformer.exceptionOccurred(e);
			if (!future.isDone()) {
				future.completeExceptionally(e);
			}
			return future;
		}
	}

	@Override
	public CompletableFuture<PutObjectResponse> putObject(Consumer<PutObjectRequest.Builder> putObjectRequest, AsyncRequestBody requestBody) {

		PutObjectRequest.Builder builder = PutObjectRequest.builder();
		putObjectRequest.accept(builder);
		PutObjectRequest request = builder.build();
		Path filePath = tempFolder.resolve(request.key());

		logger.info("Putting object: {}, resolved to path: {}", request.key(), filePath);

		return CompletableFuture.runAsync(() -> {
			try {
				Files.createDirectories(filePath.getParent());
				requestBody.subscribe(buffer -> {
					try {
						byte[] bytes = new byte[buffer.remaining()];
						buffer.get(bytes);
						Files.write(filePath, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
						logger.info("File created: {}", filePath);
					} catch (IOException e) {
						logger.error("Error writing file: {}", e.getMessage());
					}
				});
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
		}).thenApply(v -> PutObjectResponse.builder().build());
	}
}
