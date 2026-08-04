package com.fincity.saas.message.model.request.message.provider.whatsapp;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Asks this service to fetch a media file from Meta and store it.
 *
 * <p>Carries Meta's media id rather than a message row id, because the caller's messages live in
 * another service and row ids are not shared. That also means this request says nothing about who
 * may see the media: the owning service checks that before calling.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class WhatsappMediaByIdRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 2288463719911057722L;

    private String connectionName;

    /** Meta's media id, taken from the message payload by the caller. */
    private String mediaId;

    /** Where to file the result. The caller owns its own storage layout. */
    private String fileLocation;
}
