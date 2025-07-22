package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.phone.type.CodeMethodType;
import com.fincity.saas.message.model.message.whatsapp.templates.type.LanguageType;

public record RequestCode(
        @JsonProperty("code_method") CodeMethodType codeMethod, @JsonProperty("language") LanguageType language) {}
