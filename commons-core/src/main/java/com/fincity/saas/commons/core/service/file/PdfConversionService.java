package com.fincity.saas.commons.core.service.file;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.core.document.Template;
import com.fincity.saas.commons.core.feign.IFeignFilesService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.jsoup.helper.W3CDom;
import org.jsoup.parser.Parser;
import org.jsoup.parser.StreamParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

@Service
public class PdfConversionService extends AbstractTemplateConversionService {
    private static final int INITIAL_BUFFER_SIZE = 8192; // 8KB initial size
    private static final String BODY_KEY = "body";

    private final FSStreamFactory streamFactory;

    public PdfConversionService(IFeignFilesService fileService) {
        this.streamFactory = new FileServerStreamFactory(fileService);
    }

    @Override
    protected Mono<byte[]> convertToFormat(Map<String, String> processedParts, String outputFormat, Template template) {
        String htmlContent = processedParts.get(BODY_KEY);

        if (htmlContent == null || htmlContent.isBlank())
            return Mono.error(new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR, CoreMessageResourceService.TEMPLATE_DETAILS_MISSING));

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

    public Document html5ParseDocument(String htmlContent) {
        try (StreamParser streamParser = new StreamParser(Parser.htmlParser()).parse(htmlContent, "")) {
            return new W3CDom().fromJsoup(streamParser.complete());
        } catch (IOException e) {
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Error parsing HTML content using StreamParser", e);
        }
    }

    private PdfRendererBuilder createPdfBuilder(String htmlContent, ByteArrayOutputStream os) {

        Document doc = this.html5ParseDocument(htmlContent);

        return new PdfRendererBuilder()
                .useHttpStreamImplementation(streamFactory)
                .useFastMode()
                .useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory())
                .useUnicodeBidiReorderer(new ICUBidiReorderer())
                .defaultTextDirection(BaseRendererBuilder.TextDirection.LTR)
                .useDefaultPageSize(210, 297, BaseRendererBuilder.PageSizeUnits.MM)
                .useSVGDrawer(new BatikSVGDrawer())
                .withW3cDocument(doc, null)
                .toStream(os);
    }

    private byte[] generatePdf(String htmlContent, Template template, String outputFormat) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream(INITIAL_BUFFER_SIZE)) {
            PdfRendererBuilder builder = createPdfBuilder(htmlContent, os);
            builder.run();
            return os.toByteArray();
        } catch (IOException ioException) {
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    StringFormatter.format(
                            CoreMessageResourceService.TEMPLATE_CONVERT_ERROR,
                            template.getId(),
                            getFileExtension(outputFormat)),
                    ioException);
        }
    }
}
