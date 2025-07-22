package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.webhook.type.FieldType;

public record Change(
        /*
        Contains the type of notification you are getting on that Webhook. Currently, the only option for this API is “messages”.
         */
        @JsonProperty("field") FieldType field,
        /*
        A value object. Contains details of the changes related to the specified field.
         */
        @JsonProperty("value") Value value) {}
