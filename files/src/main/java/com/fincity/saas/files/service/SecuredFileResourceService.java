package com.fincity.saas.files.service;

import static com.fincity.saas.commons.util.StringUtil.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.files.dto.FilesSecuredAccessKey;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.model.FileDetail;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@Service
public class SecuredFileResourceService extends AbstractFilesResourceService {

	private static final String CREATE_KEY = "/createKey";

	@Value("${files.resources.location.secured}")
	private String location;

	private String securedResourceLocation;

	@Value("${files.timeLimit:365}")
	private Long defaultAccessTimeLimit;

	@Value("${files.timeUnit:DAYS}")
	private ChronoUnit defaultChronoUnit;

	@Value("${files.secureKeyURI:api/files/secured/downloadFileByKey/}")
	private String secureAccessPathUri;

	private final FilesSecuredAccessService fileSecuredAccessService;

	private static final Logger logger = LoggerFactory.getLogger(SecuredFileResourceService.class);

	public SecuredFileResourceService(FilesSecuredAccessService fileSecuredAccessService,
			FilesAccessPathService filesAccessPathService, FilesMessageResourceService msgService) {
		super(filesAccessPathService, msgService);
		this.fileSecuredAccessService = fileSecuredAccessService;
	}

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

	public Mono<String> createSecuredAccess(Long timeSpan, ChronoUnit timeUnit, Long accessLimit, String uri) {

		String path = uri.replace(CREATE_KEY, "");

		Tuple2<String, String> tup = super.resolvePathWithClientCode(path);

		return FlatMapUtil.flatMapMono(

				() -> this.checkReadAccessWithClientCode(tup.getT2())
						.flatMap(BooleanUtil::safeValueOfWithEmpty),

				hasReadability -> this.createAccessKey(timeSpan, timeUnit, accessLimit, tup.getT2()),

				(hasReadability, accessKey) -> Mono.just(this.secureAccessPathUri + accessKey)

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SecuredFileResourceService.createSecuredAccess"))
				.switchIfEmpty(this.msgService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						FilesMessageResourceService.SECURED_KEY_CREATION_ERROR));
	}

	public Mono<Void> downloadFileByKey(String key, DownloadOptions downloadOptions, ServerHttpRequest request,
			ServerHttpResponse response) {

		if (safeIsBlank(key)) {
			return null;
		}

		return FlatMapUtil.flatMapMono(

				() -> this.fileSecuredAccessService.getAccessPathByKey(key),

				accessPath -> {
					if (safeIsBlank(accessPath)) {
						return Mono.empty();
					}

					accessPath = super.resolvePathWithoutClientCode("", accessPath).getT1();

					Path file = Paths.get(this.securedResourceLocation, accessPath);

					if (!Files.exists(file)) {
						return this.msgService.throwMessage(
								msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								FilesMessageResourceService.PATH_NOT_FOUND, accessPath);
					}

					return Mono.just(file);
				},

				(accessPath, file) -> getFileAttributes(file),

				(accessPath, file, attr) -> {
					long fileMillis = attr.lastModifiedTime().toMillis();
					String fileETag = generateFileETag(file, fileMillis, downloadOptions);
					return super.makeMatchesStartDownload(downloadOptions, request, response, file, fileMillis,
							fileETag);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "SecuredFileResourceService.downloadFileByKey"));
	}

	private Mono<String> createAccessKey(Long time, ChronoUnit unit, Long limit, String path) {

		if (unit == null && time != null)
			return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					FilesMessageResourceService.TIME_UNIT_ERROR);

		if (time == null && limit != null)
			return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					FilesMessageResourceService.TIME_SPAN_ERROR);

		time = time == null || time.toString().isBlank() ? defaultAccessTimeLimit : time;
		unit = safeIsBlank(unit) ? defaultChronoUnit : unit;
		int pathIndex = path.indexOf('?');
		path = pathIndex != -1 ? path.substring(0, pathIndex) : path;

		FilesSecuredAccessKey fileSecuredAccessKey = new FilesSecuredAccessKey().setPath(path)
				.setAccessKey(UniqueUtil.base36UUID())
				.setAccessLimit(ULongUtil.valueOf(limit))
				.setAccessTill(LocalDateTime.now()
						.plus(time, unit));

		return fileSecuredAccessService.create(fileSecuredAccessKey).map(FilesSecuredAccessKey::getAccessKey);
	}

	private Mono<BasicFileAttributes> getFileAttributes(Path file) {

		return Mono.fromCallable(() -> Files.readAttributes(file, BasicFileAttributes.class))
				.subscribeOn(Schedulers.boundedElastic())
				.onErrorResume(IOException.class, e -> {
					logger.debug("ERROR: Unable to read attributes of file {} ", file, e);
					return Mono.empty();
				});
	}

	private String generateFileETag(Path file, long fileMillis, DownloadOptions downloadOptions) {
		return String.format("\"%d-%d-%s\"", file.hashCode(), fileMillis, downloadOptions.eTagCode());
	}

}
