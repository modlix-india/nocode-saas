package com.modlix.filesutility;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@SpringBootApplication
public class FilesutilityApplication {

	private static final String BUCKET_NAME_STATIC = "prod-static";
	private static final String BUCKET_NAME_SECURED = "local-secured";
	private static final String ENDPOINT = "https://ae81e53db5aca470c4e4073aa03498cd.r2.cloudflarestorage.com";
	private static final String ACCESS_KEY = "";
	private static final String SECRET_KEY = "";

	private static final String LOCAL_STATIC_LOCATION = "C:\\Users\\kiran\\Downloads\\imp downloads\\files\\static"; // NOSONAR
	private static final String LOCAL_SECURED_LOCATION = "C:\\Users\\kiran\\Downloads\\imp downloads\\files\\secured"; // NOSONAR

	private static final String DB_CONNECTION_STRING = "jdbc:mysql://localhost:3306/files";
	private static final String DB_USERNAME = "root";
	private static final String DB_PASSWORD = "Kiran@123";

	private static final String R2_FILE_SEPARATOR = "/";

	private static final Logger logger = LoggerFactory.getLogger(FilesutilityApplication.class);

	private static final Map<String, String> CONTENT_TYPE_MAP = new HashMap<>(Map.ofEntries(

		Map.entry(".aac", "audio/aac"), Map.entry(".abw", "application/x-abiword"),
		Map.entry(".apng", "image/apng"), Map.entry(".arc", "application/x-freearc"),
		Map.entry(".avif", "image/avif"), Map.entry(".avi", "video/x-msvideo"),
		Map.entry(".azw", "application/vnd.amazon.ebook"), Map.entry(".bin", "application/octet-stream"),
		Map.entry(".bmp", "image/bmp"), Map.entry(".bz", "application/x-bzip"),
		Map.entry(".bz2", "application/x-bzip2"), Map.entry(".cda", "application/x-cdf"),
		Map.entry(".csh", "application/x-csh"), Map.entry(".css", "text/css"), Map.entry(".csv", "text/csv"),
		Map.entry(".doc", "application/msword"),
		Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
		Map.entry(".eot", "application/vnd.ms-fontobject"), Map.entry(".epub", "application/epub+zip"),
		Map.entry(".gz", "application/gzip"), Map.entry(".gif", "image/gif"), Map.entry(".htm", "text/html"),
		Map.entry(".ico", "image/vnd.microsoft.icon"), Map.entry(".ics", "text/calendar"),
		Map.entry(".jar", "application/java-archive"), Map.entry(".jpeg", "image/jpeg"),
		Map.entry(".js", "text/javascript"), Map.entry(".json", "application/json"),
		Map.entry(".jsonld", "application/ld+json"), Map.entry(".mid", "audio/midi"),
		Map.entry(".mjs", "text/javascript"), Map.entry(".mp3", "audio/mpeg"), Map.entry(".mp4", "video/mp4"),
		Map.entry(".mpeg", "video/mpeg"), Map.entry(".mpkg", "application/vnd.apple.installer+xml"),
		Map.entry(".odp", "application/vnd.oasis.opendocument.presentation"),
		Map.entry(".ods", "application/vnd.oasis.opendocument.spreadsheet"),
		Map.entry(".odt", "application/vnd.oasis.opendocument.text"), Map.entry(".oga", "audio/ogg"),
		Map.entry(".ogv", "video/ogg"), Map.entry(".ogx", "application/ogg"), Map.entry(".opus", "audio/ogg"),
		Map.entry(".otf", "font/otf"), Map.entry(".png", "image/png"), Map.entry(".pdf", "application/pdf"),
		Map.entry(".php", "application/x-httpd-php"), Map.entry(".ppt", "application/vnd.ms-powerpoint"),
		Map.entry(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
		Map.entry(".rar", "application/vnd.rar"), Map.entry(".rtf", "application/rtf"),
		Map.entry(".sh", "application/x-sh"), Map.entry(".svg", "image/svg+xml"),
		Map.entry(".tar", "application/x-tar"), Map.entry(".tif", "image/tiff"), Map.entry(".ts", "video/mp2t"),
		Map.entry(".ttf", "font/ttf"), Map.entry(".txt", "text/plain"), Map.entry(".vsd", "application/vnd.visio"),
		Map.entry(".wav", "audio/wav"), Map.entry(".weba", "audio/webm"), Map.entry(".webm", "video/webm"),
		Map.entry(".webp", "image/webp"), Map.entry(".woff", "font/woff"), Map.entry(".woff2", "font/woff2"),
		Map.entry(".xhtml", "application/xhtml+xml"), Map.entry(".xls", "application/vnd.ms-excel"),
		Map.entry(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
		Map.entry(".xml", "application/xml"), Map.entry(".xul", "application/vnd.mozilla.xul+xml"),
		Map.entry(".zip", "application/zip"), Map.entry(".3gp", "video/3gpp"), Map.entry(".3g2", "video/3gpp2"),
		Map.entry(".7z", "application/x-7z-compressed")));

	public static void main(String[] args) throws Exception {
		S3Client s3Client = S3Client.builder()
				.region(Region.US_EAST_1)
				.endpointOverride(URI.create(ENDPOINT))
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
				.build();

		// Comment the following lines if you don't want to upload the files to
		// CloudFlare from your local machine

		// uploadFiles(s3Client, BUCKET_NAME_STATIC, LOCAL_STATIC_LOCATION, "SYSTEM");
		// // NOSONAR
		// uploadFiles(s3Client, BUCKET_NAME_SECURED, LOCAL_SECURED_LOCATION); //
		// NOSONAR

		// deleteObjectsInFolder(s3Client, BUCKET_NAME_STATIC, "fin1");

		// try (Connection connection =
		// DriverManager.getConnection(DB_CONNECTION_STRING, DB_USERNAME, DB_PASSWORD))
		// {

		// emptyTable(connection);
		// logger.info("Database connection established successfully.");

		// uploadFilesToDB(s3Client, connection, "STATIC", BUCKET_NAME_STATIC);
		// uploadFilesToDB(s3Client, connection, "SECURED", BUCKET_NAME_SECURED);
		// } catch (SQLException e) {
		// logger.error("Failed to connect to the database.", e);
		// }

		// printObjectsInFolder(s3Client, BUCKET_NAME_STATIC, "SYSTEM");

		// findObjectByName(s3Client, BUCKET_NAME_STATIC, "cedarthumb");

		// updateHeadersToInline(s3Client, BUCKET_NAME_STATIC);
	}

	public static void updateHeadersToInline(S3Client s3Client, String bucketName) {
		SdkIterable<S3Object> iterable = s3Client
				.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucketName).build())
				.contents();

		AtomicInteger count = new AtomicInteger(0);
		ExecutorService executor = Executors.newFixedThreadPool(50);
		for (S3Object s3Object : iterable) {

			int dotIndex = s3Object.key().lastIndexOf('.');
			String contentType = dotIndex == -1 ? null
					: CONTENT_TYPE_MAP.get(s3Object.key().toLowerCase().substring(dotIndex));

			executor.execute(() -> {
				s3Client.copyObject(CopyObjectRequest.builder()
						.sourceBucket(bucketName)
						.sourceKey(s3Object.key())
						.destinationBucket(bucketName)
						.destinationKey(s3Object.key())
						.metadataDirective(MetadataDirective.REPLACE)
						.contentDisposition("inline")
						.contentType(contentType)
						.build());
				var current = count.incrementAndGet();
				if (current % 100 == 0)
					logger.info("Copied {} objects.", current);
			});
		}
		executor.shutdown();
		logger.info("All objects copied. {} objects processed.", count.get());
	}

	public static void updateHeadersToInline(S3Client s3Client, String bucketName) {
		SdkIterable<S3Object> iterable = s3Client
				.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucketName).build())
				.contents();

		for (S3Object s3Object : iterable) {
			int dotIndex = s3Object.key().lastIndexOf('.');
			String contentType = dotIndex == -1 ? null
					: CONTENT_TYPE_MAP.get(s3Object.key().toLowerCase().substring(dotIndex));

			s3Client.copyObject(CopyObjectRequest.builder()
					.sourceBucket(bucketName)
					.sourceKey(s3Object.key())
					.destinationBucket(bucketName)
					.destinationKey(s3Object.key())
					.metadataDirective(MetadataDirective.REPLACE)
					.contentDisposition("inline")
					.contentType(contentType)
					.build());
		}
	}


	public static void findObjectByName(S3Client s3Client, String bucketName, String name) {

		SdkIterable<S3Object> iterable = s3Client
				.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucketName).build())
				.contents();

		for (S3Object s3Object : iterable) {
			if (s3Object.key().toUpperCase().contains(name.toUpperCase()))
				logger.info("Found object: {}", s3Object.key());
		}
	}

	public static void deleteObjectsInFolder(S3Client s3Client, String bucketName, String path) {

		SdkIterable<S3Object> iterable = s3Client
				.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucketName).prefix(path).build())
				.contents();

		for (S3Object s3Object : iterable) {
			s3Client.deleteObject(DeleteObjectRequest
					.builder()
					.bucket(bucketName)
					.key(s3Object.key())
					.build());
			logger.info("Deleted object: {}", s3Object.key());
		}
	}

	public static void printObjectsInFolder(S3Client s3Client, String bucketName, String path) {

		SdkIterable<S3Object> iterable = s3Client
				.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucketName).prefix(path).build())
				.contents();

		for (S3Object s3Object : iterable) {
			logger.info(s3Object.key());
		}
	}

	public static void emptyTable(Connection connection) {
		try {
			Statement statement = connection.createStatement();
			statement.executeUpdate("TRUNCATE TABLE files_file_system");
			logger.info("Table truncated successfully.");
		} catch (SQLException e) {
			logger.error("Failed to empty the table.", e);
		}
	}

	public static void uploadFilesToDB(S3Client s3Client, Connection connection, String type, String bucketName)
			throws SQLException {
		SdkIterable<S3Object> iterable = s3Client
				.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucketName).build())
				.contents();

		Map<String, Long> directoryIds = new HashMap<>();
		int batchSize = 0;

		PreparedStatement fileStatement = connection.prepareStatement(
				"INSERT INTO files_file_system (TYPE, CODE, NAME, FILE_TYPE, SIZE, PARENT_ID) VALUES ('" + type
						+ "', ?, ?, 'FILE', ?, ?)");

		for (S3Object s3Object : iterable) {

			String key = s3Object.key();
			int index = key.indexOf("/");
			if (index == -1)
				continue;
			String code = key.substring(0, index);
			key = key.substring(index + 1);
			index = key.lastIndexOf("/");
			String directory = index == -1 ? null : key.substring(0, index);
			String fileName = index == -1 ? key : key.substring(index + 1);

			Long parentId = findParentId(directory, code, directoryIds, type, connection);

			batchSize++;
			fileStatement.setString(1, code);
			fileStatement.setString(2, fileName);
			fileStatement.setLong(3, s3Object.size());
			if (parentId != null)
				fileStatement.setLong(4, parentId);
			else
				fileStatement.setNull(4, Types.BIGINT); // NOSONAR
			fileStatement.addBatch();

			if (batchSize == 100) {
				fileStatement.executeBatch();
				batchSize = 0;
				logger.info("Batch size reached. Executing batch.");
			}
		}

		if (batchSize > 0) {
			logger.info("Executing remaining batch.");
			fileStatement.executeBatch();
		}

		logger.info("{} files uploaded to the database successfully.", type);
	}

	public static Long findParentId(String directory, String code,
			Map<String, Long> directoryIds, String type, Connection connection) throws SQLException {

		PreparedStatement directoryStatement = connection.prepareStatement(
				"INSERT INTO files_file_system (TYPE, CODE, NAME, FILE_TYPE, PARENT_ID) VALUES ('" + type
						+ "', '" + code + "', ?, 'DIRECTORY', ?)",
				Statement.RETURN_GENERATED_KEYS);

		String directoryPath = code + (directory == null ? "" : R2_FILE_SEPARATOR + directory);
		Long parentId = directory == null || !directoryIds.containsKey(directoryPath) ? null
				: directoryIds.get(directoryPath);

		if (parentId != null || directory == null)
			return parentId;

		String[] pathParts = directory.split(R2_FILE_SEPARATOR);
		StringBuilder currentPath = new StringBuilder();

		logger.info("Creating directory: {}/{}", code, directory);
		currentPath.append(code);
		for (String pathPart : pathParts) {
			currentPath.append(R2_FILE_SEPARATOR).append(pathPart);
			if (directoryIds.containsKey(currentPath.toString())) {
				parentId = directoryIds.get(currentPath.toString());
				continue;
			}

			directoryStatement.setString(1, pathPart);
			if (parentId != null)
				directoryStatement.setLong(2, parentId);
			else
				directoryStatement.setNull(2, Types.BIGINT);

			logger.info("Inserting directory: {}", directoryStatement.toString());
			directoryStatement.execute();
			ResultSet rs = directoryStatement.getGeneratedKeys();
			if (!rs.next())
				throw new SQLException("Failed to get generated key for directory: " + currentPath.toString());
			parentId = rs.getLong(1);
			directoryIds.put(currentPath.toString(), parentId);
		}

		return parentId;
	}

	public static void uploadFiles(S3Client s3Client, String bucketName, String localLocation, String fromPath)
			throws IOException {

		Path path = Paths.get(localLocation);

		if (fromPath != null && !fromPath.isEmpty())
			path = path.resolve(fromPath);

		Files.walk(path)
				.filter(Files::isRegularFile)
				.forEach(file -> {
					try {
						String filePath = file.toAbsolutePath().toString();
						String fileName = file.getFileName().toString();
						int index = fileName.lastIndexOf('.');
						String contentType = index == -1 ? null
								: CONTENT_TYPE_MAP.get(filePath.substring(index).toLowerCase());
						String key = filePath.substring(localLocation.length() + 1).trim().replace("\\", "/");
						if (key.startsWith("/"))
							key = key.substring(1);
						s3Client.putObject(
								PutObjectRequest.builder()
										.bucket(bucketName)
										.contentDisposition(
												"inline; filename=\"" + fileName + "\"")
										.contentType(contentType)
										.key(key)
										.build(),
								RequestBody.fromFile(file));
						logger.info("File uploaded successfully: {}", key);
					} catch (SdkClientException | SdkServiceException e) {
						logger.error("Failed to upload file: {}", file.toAbsolutePath(), e);
					}
				});
	}

	public static void uploadFiles(S3Client s3Client, String bucketName, String localLocation) throws IOException {

		uploadFiles(s3Client, bucketName, localLocation, "");
	}
}
