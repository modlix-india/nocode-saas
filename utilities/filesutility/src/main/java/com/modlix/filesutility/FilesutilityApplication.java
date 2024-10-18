package com.modlix.filesutility;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@SpringBootApplication
public class FilesutilityApplication {

	private static final String BUCKET_NAME_STATIC = "local-static";
	private static final String BUCKET_NAME_SECURED = "local-secured";
	private static final String ENDPOINT = "https://ae81e53db5aca470c4e4073aa03498cd.r2.cloudflarestorage.com";
	private static final String ACCESS_KEY = "2229a18802734bc30a8419a2e622187c";
	private static final String SECRET_KEY = "777f222e215a5ae44da12e258f6f93d7d67c38d47801ee3d7c80adfbf06a5ef5";

	private static final String LOCAL_STATIC_LOCATION = "C:\\Users\\kiran\\Downloads\\files\\static"; // NOSONAR
	private static final String LOCAL_SECURED_LOCATION = "C:\\Users\\kiran\\Downloads\\files\\secured"; // NOSONAR

	private static final String DB_CONNECTION_STRING = "jdbc:mysql://localhost:3306/files";
	private static final String DB_USERNAME = "root";
	private static final String DB_PASSWORD = "Kiran@123";

	private static final String R2_FILE_SEPARATOR = "/";

	private static final Logger logger = LoggerFactory.getLogger(FilesutilityApplication.class);

	public static void main(String[] args) throws Exception {
		S3Client s3Client = S3Client.builder()
				.region(Region.US_EAST_1)
				.endpointOverride(URI.create(ENDPOINT))
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
				.build();

		// Comment the following lines if you don't want to upload the files to
		// CloudFlare from your local machine

		// uploadFiles(s3Client, BUCKET_NAME_STATIC, LOCAL_STATIC_LOCATION); //NOSONAR
		// uploadFiles(s3Client, BUCKET_NAME_SECURED, LOCAL_SECURED_LOCATION); //NOSONAR

		try (Connection connection = DriverManager.getConnection(DB_CONNECTION_STRING, DB_USERNAME, DB_PASSWORD)) {

			emptyTable(connection);
			logger.info("Database connection established successfully.");

			uploadFilesToDB(s3Client, connection, "STATIC", BUCKET_NAME_STATIC);
			uploadFilesToDB(s3Client, connection, "SECURED", BUCKET_NAME_SECURED);

		} catch (SQLException e) {
			logger.error("Failed to connect to the database.", e);
		}
	}

	public static void emptyTable(Connection connection) {
		try {
			Statement statement = connection.createStatement();
			statement.executeUpdate("TRUNCATE TABLE file_system");
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
				"INSERT INTO file_system (TYPE, CODE, NAME, FILE_TYPE, SIZE, PARENT_ID) VALUES ('" + type
						+ "', ?, ?, 'FILE', ?, ?)");

		for (S3Object s3Object : iterable) {

			String key = s3Object.key();
			int index = key.indexOf("/");
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
				"INSERT INTO file_system (TYPE, CODE, NAME, FILE_TYPE, PARENT_ID) VALUES ('" + type
						+ "', ?, ?, 'DIRECTORY', ?)",
				Statement.RETURN_GENERATED_KEYS);

		String directoryPath = code + (directory == null ? "" : R2_FILE_SEPARATOR + directory);
		Long parentId = directory == null || !directoryIds.containsKey(directoryPath) ? null
				: directoryIds.get(directoryPath);
		if (parentId == null && directory != null) {

			String[] pathParts = directory.split(R2_FILE_SEPARATOR);
			StringBuilder currentPath = new StringBuilder();
			directoryStatement.setString(1, code);

			logger.info("Creating directory: {}/{}", code, directory);
			for (String pathPart : pathParts) {
				currentPath.append(R2_FILE_SEPARATOR).append(pathPart);
				if (directoryIds.containsKey(code + R2_FILE_SEPARATOR + pathPart)) {
					parentId = directoryIds.get(code + R2_FILE_SEPARATOR + pathPart);
					continue;
				}

				directoryStatement.setString(2, pathPart);
				if (parentId != null)
					directoryStatement.setLong(3, parentId);
				else
					directoryStatement.setNull(3, Types.BIGINT); // NOSONAR
				directoryStatement.execute();
				ResultSet rs = directoryStatement.getGeneratedKeys();
				if (!rs.next())
					throw new SQLException("Failed to get generated key for directory: " + currentPath.toString());
				parentId = rs.getLong(1);
				directoryIds.put(code + currentPath.toString(), parentId);
			}
			directoryIds.put(directoryPath, parentId);
		}

		return parentId;
	}

	public static void uploadFiles(S3Client s3Client, String bucketName, String localLocation) throws IOException {

		Files.walk(Paths.get(localLocation))
				.filter(Files::isRegularFile)
				.forEach(file -> {
					try {
						String filePath = file.toAbsolutePath().toString();
						String key = filePath.substring(localLocation.length() + 1).trim().replace("\\", "/");
						if (key.startsWith("/"))
							key = key.substring(1);
						s3Client.putObject(
								PutObjectRequest.builder()
										.bucket(bucketName)
										.contentDisposition(
												"attachment; filename=\"" + file.getFileName().toString() + "\"")
										.key(key)
										.build(),
								RequestBody.fromFile(file));
						logger.info("File uploaded successfully: {}", key);
					} catch (SdkClientException | SdkServiceException e) {
						logger.error("Failed to upload file: {}", file.toAbsolutePath(), e);
					}
				});
	}
}
