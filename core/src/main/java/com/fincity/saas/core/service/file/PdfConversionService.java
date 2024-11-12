package com.fincity.saas.core.service.file;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.document.Template;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

@Service
public class PdfConversionService extends AbstractTemplateConversionService implements ITemplateConversionService {

	private static final int INITIAL_BUFFER_SIZE = 8192; // 8KB initial size
	private static final String BODY_KEY = "body";

	private final FSStreamFactory streamFactory;

	public PdfConversionService() {
		this.streamFactory = new SpringWebClientFSStreamFactory();
	}

	protected Mono<byte[]> convertToFormat(Map<String, String> processedParts, String outputFormat, Template template) {

		String htmlContent = processedParts.get(BODY_KEY);

		if (htmlContent == null || htmlContent.isBlank()) {
			return Mono.error(new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
					CoreMessageResourceService.TEMPLATE_DETAILS_MISSING));
		}

		return Mono.fromCallable(() -> generatePdf(htmlContent, template, outputFormat))
				.subscribeOn(Schedulers.boundedElastic())
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "TemplateConversionService.convert"));
	}

	@Override
	public MediaType getMediaType(String outputFormat) {
		return MediaType.APPLICATION_PDF;
	}

	@Override
	public String getFileExtension(String outputFormat) {
		return "." + getMediaType(outputFormat).getSubtype();
	}

	private PdfRendererBuilder createPdfBuilder(String htmlContent, ByteArrayOutputStream os) {

		return new PdfRendererBuilder()
				.useHttpStreamImplementation(streamFactory)
				.useFastMode()
				.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory())
				.useUnicodeBidiReorderer(new ICUBidiReorderer())
				.defaultTextDirection(BaseRendererBuilder.TextDirection.LTR)
				.withHtmlContent(htmlContent, null)
				.toStream(os);
	}

	private byte[] generatePdf(String htmlContent, Template template, String outputFormat) {
		try (ByteArrayOutputStream os = new ByteArrayOutputStream(INITIAL_BUFFER_SIZE)) {
			PdfRendererBuilder builder = createPdfBuilder(htmlContent, os);
			builder.run();
			return os.toByteArray();
		} catch (IOException ioException) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
					StringFormatter.format(CoreMessageResourceService.TEMPLATE_CONVERT_ERROR,
							template.getId(),
							getFileExtension(outputFormat)),
					ioException);
		}
	}

	private static class SpringWebClientFSStreamFactory implements FSStreamFactory {

		private final RestClient restClient;

		private final Map<String, byte[]> resourceCache = new ConcurrentHashMap<>();

		public SpringWebClientFSStreamFactory() {
			this.restClient = RestClient.create();
		}

		@Override
		public FSStream getUrl(String url) {

			try {
				byte[] responseBytes = resourceCache.computeIfAbsent(url, this::fetchResource);

				if (responseBytes == null) {
					return null;
				}

				return new FSStream() {
					@Override
					public InputStream getStream() {
						return new ByteArrayInputStream(responseBytes);
					}

					@Override
					public Reader getReader() {
						return new InputStreamReader(getStream(), StandardCharsets.UTF_8);
					}
				};
			} catch (Exception e) {
				throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
						StringFormatter.format(CoreMessageResourceService.FS_STREAM_ERROR));
			}
		}

		private byte[] fetchResource(String url) {
			return restClient.get().uri(url).retrieve().body(byte[].class);
		}
	}
}
