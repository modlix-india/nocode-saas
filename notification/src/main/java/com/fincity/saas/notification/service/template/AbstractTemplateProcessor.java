package com.fincity.saas.notification.service.template;

import java.io.IOException;
import java.io.StringWriter;
import java.util.TimeZone;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

public abstract class AbstractTemplateProcessor {

	protected static final Configuration DEFAULT = new Configuration(Configuration.VERSION_2_3_33);
	private static final int INITIAL_BUFFER_SIZE = 4096;

	static {
		DEFAULT.setDefaultEncoding("UTF-8");
		DEFAULT.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		DEFAULT.setLogTemplateExceptions(false);
		DEFAULT.setWrapUncheckedExceptions(true);
		DEFAULT.setFallbackOnNullLoopVariable(false);
		DEFAULT.setSQLDateAndTimeTimeZone(TimeZone.getDefault());
		DEFAULT.setTemplateLoader(new StringTemplateLoader());
	}

	public Configuration getConfiguration() {
		return DEFAULT;
	}

	public int getInitialBufferSize() {
		return INITIAL_BUFFER_SIZE;
	}

	public Template toTemplate(String templateName, String content) throws IOException {
		return new Template(templateName, content, this.getConfiguration());
	}

	public String toString(String templateName, String content) throws IOException, TemplateException {
		return this.toString(this.toTemplate(templateName, content), null);
	}

	public String toString(Template template, Object model) throws IOException, TemplateException {
		StringWriter result = new StringWriter(this.getInitialBufferSize());
		template.process(model, result);
		return result.toString();
	}
}
