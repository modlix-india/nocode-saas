package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Entry(

        /*
        Changes that triggered the Webhooks call. This field contains an array of change objects.
        */
        @JsonProperty("changes") List<Change> changes,
        /*
            The ID of Whatsapp Business Accounts this Webhook belongs to.
        */
        @JsonProperty("id") String id,
        @JsonProperty("time") long time) {}
