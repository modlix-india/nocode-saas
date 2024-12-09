package com.fincity.saas.core.service.file;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.core.feign.IFeignFilesService;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;

import reactor.core.publisher.Mono;

@Component
public class FileServerStreamFactory implements FSStreamFactory {

	private final IFeignFilesService filesService;
	private final WebClient webClient;
	private final ConcurrentHashMap<String, byte[]> cache;

	private static final Pattern FILE_SERVER_SECURED_PATTERN = Pattern.compile(
			".*/api/files/secured/file/(.*)");

	public FileServerStreamFactory(IFeignFilesService filesService) {
		this.filesService = filesService;
		this.webClient = WebClient.create();
		this.cache = new ConcurrentHashMap<>();
	}

	@Override
	public FSStream getUrl(String url) {

		if (url == null || url.isEmpty()) {
			return null;
		}

		try {
			final byte[] resource = getResource(url);

			if (resource == null) {
				return null;
			}

			return new FSStream() {

				@Override
				public InputStream getStream() {
					return new ByteArrayInputStream(resource);
				}

				@Override
				public Reader getReader() {
					return new InputStreamReader(getStream());
				}
			};
		} catch (Exception e) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
					StringFormatter.format(CoreMessageResourceService.FS_STREAM_ERROR));
		}
	}

	private byte[] getResource(String url) {

		if (cache.containsKey(url)) {
			return cache.get(url);
		}

		return FlatMapUtil.flatMapMono(
				() -> isFileServerUrl(url) ? fetchInternalResource(url) : fetchExternalResource(url),
				resourceBuffer -> {
					cache.putIfAbsent(url, resourceBuffer);
					return Mono.just(resourceBuffer);
				})
				.block();
	}

	private Mono<byte[]> fetchInternalResource(String url) {

		String path = extractFilePath(url);

		return filesService.downloadFile("secured", path, null, null, false, true, null,
				null, false, null)
				.map(resourceBuffer -> {
					byte[] bytes = new byte[resourceBuffer.remaining()];
					resourceBuffer.get(bytes);
					return bytes;
				})
				.onErrorMap(e -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
						StringFormatter.format(CoreMessageResourceService.UNABLE_TO_FETCH_INTERNAL_RESOURCE)));
	}

	private String extractFilePath(String url) {
		var matcher = FILE_SERVER_SECURED_PATTERN.matcher(url);

		if (matcher.find()) {
			return matcher.group(1);
		}

		return "";
	}

	private Mono<byte[]> fetchExternalResource(String url) {
		return webClient.get().uri(url).retrieve().bodyToMono(byte[].class)
				.onErrorMap(e -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
						StringFormatter.format(CoreMessageResourceService.UNABLE_TO_FETCH_EXTERNAL_RESOURCE)));
	}

	private boolean isFileServerUrl(String url) {
		return FILE_SERVER_SECURED_PATTERN.matcher(url).matches();
	}

	public void clearCache() {
		cache.clear();
	}
}
