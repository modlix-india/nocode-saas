package com.fincity.saas.files.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileSystemUtils;

import com.fincity.saas.commons.exeception.GenericException;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class MockS3AsyncClient {

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
		FileSystemUtils.deleteRecursively(tempFolder.toFile());
	}

	@Override
	public <ReturnT> CompletableFuture<ReturnT> getObject(GetObjectRequest getObjectRequest, AsyncResponseTransformer<GetObjectResponse, ReturnT> asyncResponseTransformer) {
		Path filePath = tempFolder.resolve(getObjectRequest.key());

		if (!Files.exists(filePath)) {
			CompletableFuture<ReturnT> future = new CompletableFuture<>();
			future.completeExceptionally(new GenericException(HttpStatus.NOT_FOUND,
					"File not found: " + getObjectRequest.key()));
			return future;
		}

		CompletableFuture<ReturnT> future = asyncResponseTransformer.prepare();

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
		Path filePath = tempFolder.resolve(builder.build().key());

		return CompletableFuture.runAsync(() -> {
			try {
				Files.createDirectories(filePath.getParent());
				requestBody.subscribe(new Subscriber<>() {
					@Override
					public void onSubscribe(Subscription s) {
						s.request(Long.MAX_VALUE);
					}

					@Override
					public void onNext(ByteBuffer byteBuffer) {
						try {
							Files.write(filePath, byteBuffer.array(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
						} catch (IOException e) {
							logger.error(e.getMessage());
						}
					}

					@Override
					public void onError(Throwable t) {
						logger.error(t.getMessage());
					}

					@Override
					public void onComplete() {
						// Completed writing to file
					}
				});
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
		}).thenApply(v -> PutObjectResponse.builder().build());
	}
}
