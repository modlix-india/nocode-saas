package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;

@JsonInclude(Include.NON_NULL)
public class DocumentParameter extends Parameter {

    private Document document;

    public DocumentParameter() {
        super(ParameterType.DOCUMENT);
    }

    public DocumentParameter(Document document) {
        super(ParameterType.DOCUMENT);
        this.document = document;
    }

    public Document getDocument() {
        return document;
    }

    public DocumentParameter setDocument(Document document) {
        this.document = document;
        return this;
    }
}
