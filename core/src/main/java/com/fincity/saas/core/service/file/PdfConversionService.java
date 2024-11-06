package com.fincity.saas.core.service.file;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.document.Template;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

@Service
public class PdfConversionService extends AbstractTemplateConversionService implements ITemplateConversionService {

	private static final int INITIAL_BUFFER_SIZE = 8192; // 8KB initial size
	private static final String BODY_KEY = "body";

	protected Mono<byte[]> convertToFormat(Map<String, String> processedParts, String outputFormat, Template template) {
		return Mono.fromCallable(() -> {

			String htmlContent = processedParts.get(BODY_KEY);
			if (htmlContent == null || htmlContent.isBlank()) {
				throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
						CoreMessageResourceService.TEMPLATE_DETAILS_MISSING);
			}

			try (ByteArrayOutputStream os = new ByteArrayOutputStream(INITIAL_BUFFER_SIZE)) {
				PdfRendererBuilder builder = new PdfRendererBuilder();

				builder.useFastMode()
						.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory())
						.useUnicodeBidiReorderer(new ICUBidiReorderer())
						.defaultTextDirection(BaseRendererBuilder.TextDirection.LTR)
						.withHtmlContent(htmlContent, null)
						.toStream(os);

				builder.run();
				return os.toByteArray();
			} catch (IOException ioException) {
				throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
						StringFormatter.format(CoreMessageResourceService.TEMPLATE_CONVERT_ERROR, template.getId(),
								getFileExtension(outputFormat)),
						ioException);
			}
		}).contextWrite(Context.of(LogUtil.METHOD_NAME, "TemplateConversionService.convert"))
				.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public MediaType getMediaType(String outputFormat) {
		return MediaType.APPLICATION_PDF;
	}

	@Override
	public String getFileExtension(String outputFormat) {
		return "." + getMediaType(outputFormat).getSubtype();
	}
}
