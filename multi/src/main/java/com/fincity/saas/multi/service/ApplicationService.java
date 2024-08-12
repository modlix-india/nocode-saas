package com.fincity.saas.multi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.multi.dto.MultiApp;
import com.fincity.saas.multi.dto.MultiAppUpdate;
import com.fincity.saas.multi.fiegn.IFeignCoreService;
import com.fincity.saas.multi.fiegn.IFeignUIService;
import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.checkerframework.checker.units.qual.s;
import org.jooq.meta.derby.sys.Sys;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@Service
public class ApplicationService {

	private static final String CLIENT_CODE = "clientCode";
	private static final String APP_CODE = "appCode";

	private final IFeignSecurityService securityService;
	private final IFeignCoreService coreService;
	private final IFeignUIService uiService;
	private final MultiMessageResourceService messageResourceService;
	private final ObjectMapper objectMapper;

	public ApplicationService(IFeignSecurityService securityService, IFeignCoreService coreService,
			IFeignUIService uiService, MultiMessageResourceService messageResourceService, ObjectMapper objectMapper) {
		this.securityService = securityService;
		this.coreService = coreService;
		this.uiService = uiService;
		this.messageResourceService = messageResourceService;
		this.objectMapper = objectMapper;
	}

	public Mono<Void> transport(
			String forwardedHost,
			String forwardedPort,
			String clientCode,
			String headerAppCode,
			String appCode, ServerHttpResponse response) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.securityService.makeTransport(ca.getAccessToken(), forwardedHost,
						forwardedPort, clientCode,
						headerAppCode, appCode),

				(ca, security) -> this.coreService.makeTransport(ca.getAccessToken(), forwardedHost,
						forwardedPort,
						clientCode, headerAppCode,
						Map.of(APP_CODE, appCode, CLIENT_CODE, ca.getClientCode())),

				(ca, security, core) -> this.uiService.makeTransport(ca.getAccessToken(), forwardedHost,
						forwardedPort,
						clientCode, headerAppCode,
						Map.of(APP_CODE, appCode, CLIENT_CODE, ca.getClientCode())),

				(ca, security, core, ui) -> {

					ZeroCopyHttpOutputMessage zeroCopyResponse = (ZeroCopyHttpOutputMessage) response;
					HttpHeaders headers = response.getHeaders();
					headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
					headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + appCode + "_"
							+ clientCode + ".modl");

					try {
						Path tempDir = Files.createTempDirectory("transport");
						Path path = tempDir.resolve("transport.zip");

						File file = path.toFile();

						FileSystem zipfs = FileSystems.newFileSystem(URI.create("jar:" + path.toUri().toString()),
								Map.of("create", "true"));

						Files.write(zipfs.getPath("/security.json"), this.objectMapper.writeValueAsBytes(security));
						Files.write(zipfs.getPath("/core.cmodl"), core.array());
						Files.write(zipfs.getPath("/ui.umodl"), ui.array());

						zipfs.close();

						return zeroCopyResponse.writeWith(file, 0, file.length()).doFinally(e -> {
							try {
								Files.deleteIfExists(path);
							} catch (Exception ex) {
								throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
							}
						});
					} catch (Exception e) {
						throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
					}

				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.transport"));
	}

	public Mono<App> createApplication(
			String forwardedHost,
			String forwardedPort,
			String clientCode,
			String headerAppCode, MultiApp application) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (!SecurityContextUtil.hasAuthority("Authorities.Application_CREATE",
							ca.getAuthorities())) {
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								MultiMessageResourceService.FORBIDDEN_CREATE,
								"Application");
					}

					if (application.getAppId() == null)
						return Mono.just(Optional.<App>empty());

					return this.securityService
							.getAppById(ca.getAccessToken(), forwardedHost, forwardedPort,
									clientCode, headerAppCode,
									application.getAppId().toString())
							.map(Optional::of);
				},

				(ca, app) -> {

					if (!app.isEmpty())
						return Mono.just(app.get());

					if (StringUtil.safeIsBlank(application.getAppAccessType())
							|| "OWN".equals(application.getAppAccessType())) {
						application.setAppAccessType("OWN");
					}

					application.setClientId(ULongUtil.valueOf(ca.getUser().getClientId()));

					App secApp = new App();
					secApp.setAppCode(application.getAppCode());
					secApp.setAppName(application.getAppName());
					secApp.setAppType(application.getAppType());
					secApp.setAppAccessType(application.getAppAccessType());
					secApp.setClientId(
							application.getClientId() == null ? ca.getUser().getClientId()
									: application.getClientId().toBigInteger());

					return this.securityService
							.createApp(ca.getAccessToken(), forwardedHost, forwardedPort,
									clientCode, headerAppCode, secApp)
							.flatMap(newApp -> this.addDefinition(ca.getAccessToken(),
									forwardedHost, forwardedPort,
									clientCode, headerAppCode, application.setAppCode(newApp.getAppCode()))
									.map(e -> newApp));
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.createApplication"));
	}

	public Mono<Boolean> addDefinition(String accessToken,
			String forwardedHost,
			String forwardedPort,
			String clientCode,
			String headerAppCode,
			MultiApp application) {

		boolean hasDefinition = !StringUtil.safeIsBlank(application.getTransportDefinitionURL())
				|| (application.getTransportDefinition() != null
						&& !application.getTransportDefinition().isEmpty())
				|| !StringUtil
						.safeIsBlank(application.getEncodedModl());

		if (!hasDefinition)
			return Mono.just(false);

		Mono<Path> fileMono = null;

		Path tempDir = null;
		try {
			tempDir = Files.createTempDirectory("transport");
		} catch (Exception ex) {
			return this.messageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
					MultiMessageResourceService.MULTI_TRANSPORT_ERROR);
		}
		Path file = tempDir.resolve("data");

		if (!StringUtil.safeIsBlank(application.getTransportDefinitionURL())) {

			fileMono = WebClient.builder().exchangeStrategies(
					ExchangeStrategies.builder().codecs(
							clientCodecConfigurer -> clientCodecConfigurer
									.defaultCodecs()
									.maxInMemorySize(
											50 * 1024 * 1024))
							.build())
					.baseUrl(
							application.getTransportDefinitionURL())
					.build().get().retrieve().bodyToMono(byte[].class)
					.flatMap(e -> Mono.fromCallable(() -> {
						Files.write(file, e);
						return file;
					}));
		} else if (!StringUtil.safeIsBlank(application.getEncodedModl())) {
			fileMono = Mono.fromCallable(() -> {
				Files.write(file, Base64.getEncoder().encode(application.getEncodedModl().getBytes()));
				return file;
			});
		} else {
			fileMono = Mono.fromCallable(() -> {
				Files.write(file, this.objectMapper.writeValueAsBytes(application.getTransportDefinition()));
				return file;
			});
		}

		final Mono<Path> finalFileMono = fileMono;
		return FlatMapUtil.flatMapMonoWithNull(

				() -> finalFileMono,

				f -> this.securityService
						.findBaseClientCodeForOverride(accessToken, forwardedHost,
								forwardedPort, clientCode,
								headerAppCode, application.getAppCode())
						.map(Tuple2::getT1),

				(f, cc) -> {
					try {
						return Mono.just(Files.readString(f).trim().startsWith("{"));
					} catch (Exception ex) {
						FlatMapUtil.logValue(ex.toString());
						return Mono.just(false);
					}
				},

				(f, cc, isJson) -> isJson.booleanValue()
						? this.startJSONTransport(f, accessToken, forwardedHost, forwardedPort, clientCode,
								headerAppCode, true, cc, application.getAppCode())
						: this.startZipTransport(f, accessToken, forwardedHost, forwardedPort, clientCode,
								headerAppCode, true, cc, application.getAppCode()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.addDefinition"));

	}

	@SuppressWarnings("unchecked")
	public Mono<Boolean> startJSONTransport(Path file, String accessToken, String forwardedHost, String forwardedPort,
			String clientCode, String headerAppCode, Boolean isBaseApp, String cc, String appCode) {

		try {
			Map<String, Object> definition = this.objectMapper.readValue(file.toFile(), Map.class);

			Mono<Boolean> security = this.securityService.createAndApplyTransport(accessToken,
					forwardedHost, forwardedPort,
					clientCode, headerAppCode,
					makeAppCodeChanges((Map<String, Object>) definition.get("security"),
							appCode,
							cc));

			ByteBuffer coreObj = ByteBuffer.wrap(this.objectMapper.writeValueAsBytes(makeAppCodeChanges(
					(Map<String, Object>) definition
							.get("core"),
					appCode, cc)));
			Mono<Boolean> core = this.coreService
					.createAndApplyTransport(accessToken, forwardedHost,
							forwardedPort,
							clientCode, headerAppCode, true, isBaseApp, appCode, "core.json",
							coreObj)
					.map(e -> true);

			ByteBuffer uiObj = ByteBuffer.wrap(this.objectMapper.writeValueAsBytes(makeAppCodeChanges(
					(Map<String, Object>) definition
							.get("ui"),
					appCode, cc)));
			Mono<Boolean> ui = this.uiService
					.createAndApplyTransport(accessToken, forwardedHost,
							forwardedPort,
							clientCode, headerAppCode, true, isBaseApp, appCode, "ui.json",
							uiObj)
					.map(e -> true);

			return FlatMapUtil.flatMapMonoWithNull(() -> security, x -> core, (x, y) -> ui).contextWrite(Context.of(
					LogUtil.METHOD_NAME, "ApplicationService.startJSONTransport"));
		} catch (Exception ex) {
			return this.messageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
					MultiMessageResourceService.MULTI_TRANSPORT_ERROR);
		}
	}

	@SuppressWarnings("unchecked")
	public Mono<Boolean> startZipTransport(Path file, String accessToken, String forwardedHost, String forwardedPort,
			String clientCode, String headerAppCode, Boolean isBaseApp, String cc, String appCode) {

		try {
			FileSystem zipfs = FileSystems.newFileSystem(URI.create("jar:" + file.toUri().toString()), Map.of());

			Path securityJson = zipfs.getPath("/security.json");

			Map<String, Object> securityObj = this
					.makeAppCodeChanges(this.objectMapper.readValue(Files.readAllBytes(securityJson),
							Map.class), appCode, cc);

			Mono<Boolean> security = this.securityService.createAndApplyTransport(accessToken,
					forwardedHost, forwardedPort,
					clientCode, headerAppCode, securityObj);

			Mono<Boolean> core = this.uiService
					.createAndApplyTransport(accessToken, forwardedHost, forwardedPort, clientCode, headerAppCode,
							false, isBaseApp, appCode, "core.cmodl",
							this.makeAppCodeChanges(file.getParent(), zipfs.getPath("/core.cmodl"), appCode, cc))
					.map(e -> true);

			Mono<Boolean> ui = this.uiService
					.createAndApplyTransport(accessToken, forwardedHost, forwardedPort, clientCode, headerAppCode,
							false, isBaseApp, appCode, "ui.umodl",
							this.makeAppCodeChanges(file.getParent(), zipfs.getPath("/ui.umodl"), appCode, cc))
					.map(e -> true);

			return FlatMapUtil.flatMapMonoWithNull(() -> security, x -> core, (x, y) -> ui).contextWrite(Context.of(
					LogUtil.METHOD_NAME, "ApplicationService.startZipTransport"));
		} catch (Exception ex) {
			return this.messageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
					MultiMessageResourceService.MULTI_TRANSPORT_ERROR);
		}
	}

	@SuppressWarnings("unchecked")
	public ByteBuffer makeAppCodeChanges(Path folder, Path fileInZip, String appCode, String clientCode) {

		Path modlFile = folder.resolve(fileInZip.getFileName().toString());

		try {

			// Path newModlFile = folder.resolve("new" +
			// fileInZip.getFileName().toString());

			Files.copy(fileInZip, modlFile);

			FileSystem zipfs = FileSystems.newFileSystem(URI.create("jar:" + modlFile.toUri().toString()),
					Map.of("create", "true"));
			// FileSystem newZipfs = FileSystems.newFileSystem(URI.create("jar:" +
			// newModlFile.toUri().toString()),
			// Map.of("create", "true"));

			Path root = zipfs.getPath("/");

			List<Path> files = Files.walk(root).collect(Collectors.toList());

			for (Path p : files) {

				if (Files.isDirectory(p)) {
					// if (p.getFileName() != null && !p.getFileName().toString().equals("/"))

					continue;
				}

				Map<String, Object> m = this.objectMapper.readValue(Files.readString(p), Map.class);
				if (m.containsKey(APP_CODE))
					m.put(APP_CODE, appCode);

				if (m.containsKey(CLIENT_CODE))
					m.put(CLIENT_CODE, clientCode);

				if (p.getParent().getFileName() != null
						&& (p.getParent().getFileName().toString().equals("Application") ||
								p.getParent().getFileName().toString().equals("Filler")))
					m.put("name", appCode);

				Files.write(p, this.objectMapper.writeValueAsBytes(m),
						StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
						StandardOpenOption.CREATE);
			}

			// newZipfs.close();
			zipfs.close();

		} catch (Exception e) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}

		try {
			return ByteBuffer.wrap(Files.readAllBytes(modlFile));
		} catch (Exception e) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	public Mono<Boolean> updateApplication(String forwardedHost,
			String forwardedPort, String clientCode, String headerAppCode, MultiAppUpdate application) {

		boolean hasDefinition = !StringUtil.safeIsBlank(application.getTransportDefinitionURL())
				|| (application.getTransportDefinition() != null
						&& !application.getTransportDefinition().isEmpty())
				|| !StringUtil
						.safeIsBlank(application.getEncodedModl());

		if (!hasDefinition)
			return Mono.just(false);

		Mono<Path> fileMono = null;

		Path tempDir = null;

		try {
			tempDir = Files.createTempDirectory("transport");
		} catch (Exception ex) {
			return this.messageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
					MultiMessageResourceService.MULTI_TRANSPORT_ERROR);
		}
		Path file = tempDir.resolve("data");

		if (!StringUtil.safeIsBlank(application.getTransportDefinitionURL())) {

			fileMono = WebClient.builder().exchangeStrategies(
					ExchangeStrategies.builder().codecs(
							clientCodecConfigurer -> clientCodecConfigurer
									.defaultCodecs()
									.maxInMemorySize(
											50 * 1024 * 1024))
							.build())
					.baseUrl(
							application.getTransportDefinitionURL())
					.build().get().retrieve().bodyToMono(byte[].class)
					.flatMap(e -> Mono.fromCallable(() -> {
						Files.write(file, e);
						return file;
					}));
		} else if (!StringUtil.safeIsBlank(application.getEncodedModl())) {
			fileMono = Mono.fromCallable(() -> {
				Files.write(file, Base64.getEncoder().encode(application.getEncodedModl().getBytes()));
				return file;
			});
		} else {
			fileMono = Mono.fromCallable(() -> {
				Files.write(file, this.objectMapper.writeValueAsBytes(application.getTransportDefinition()));
				return file;
			});
		}

		Mono<Path> finalFileMono = fileMono;

		return FlatMapUtil.flatMapMonoWithNull(

				() -> finalFileMono,

				f -> SecurityContextUtil.getUsersContextAuthentication()
						.map(ContextAuthentication::getAccessToken),

				(f, accessToken) -> this.securityService
						.findBaseClientCodeForOverride(accessToken, forwardedHost,
								forwardedPort, clientCode,
								headerAppCode, application.getAppCode())
						.map(Tuple2::getT1),

				(f, accessToken, cc) -> {
					try {
						return Mono.just(Files.readString(f).trim().startsWith("{"));
					} catch (Exception ex) {
						FlatMapUtil.logValue(ex.toString());
						return Mono.just(false);
					}
				},

				(f, accessToken, cc, isJson) -> isJson.booleanValue()
						? this.startJSONTransport(f, accessToken, forwardedHost, forwardedPort, clientCode,
								headerAppCode, application.getIsBaseUpdate(), cc, application.getAppCode())
						: this.startZipTransport(f, accessToken, forwardedHost, forwardedPort, clientCode,
								headerAppCode, application.getIsBaseUpdate(), cc, application.getAppCode()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.updateApplication"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> makeAppCodeChanges(Map<String, Object> map, String appCode, String clientCode) {

		if (map == null || map.isEmpty())
			return map;

		map.put(APP_CODE, appCode);
		map.put(CLIENT_CODE, clientCode);
		map.put("uniqueTransportCode", UniqueUtil.shortUUID());

		if (map.get("objects") instanceof List<?> lst)
			for (Object e : lst) {
				if (e instanceof Map<?, ?> exMap && exMap.get("data") instanceof Map<?, ?> dataMap) {
					Map<String, Object> inMap = (Map<String, Object>) dataMap;
					inMap.put(APP_CODE, appCode);
					inMap.put(CLIENT_CODE, clientCode);

					if ("Application".equals(exMap.get("objectType")) || "Filler".equals(exMap.get("objectType"))) {
						inMap.put("name", appCode);
					}
				}
			}

		return map;
	}
}
