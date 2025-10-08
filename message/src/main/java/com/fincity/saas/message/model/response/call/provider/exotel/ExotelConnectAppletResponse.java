package com.fincity.saas.message.model.response.call.provider.exotel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExotelConnectAppletResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 5952437367251012984L;

    @JsonProperty("fetch_after_attempt")
    private Boolean fetchAfterAttempt;

    @JsonProperty("destination")
    private Destination destination;

    @JsonProperty("outgoing_phone_number")
    private String outgoingPhoneNumber;

    @JsonProperty("record")
    private Boolean doRecord;

    @JsonProperty("recording_channels")
    private String recordingChannels;

    @JsonProperty("max_ringing_duration")
    private Long maxRingingDuration;

    @JsonProperty("max_conversation_duration")
    private Long maxConversationDuration;

    @JsonProperty("music_on_hold")
    private MusicOnHold musicOnHold;

    @JsonProperty("parallel_ringing")
    private ParallelRinging parallelRinging;

    @JsonProperty("dial_passthru_event_url")
    private String dialPassthruEventUrl;

    @JsonProperty("start_call_playback")
    private StartCallPlayback startCallPlayback;

    @Data
    @Accessors(chain = true)
    @FieldNameConstants
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Destination implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("numbers")
        private List<String> numbers;
    }

    @Data
    @Accessors(chain = true)
    @FieldNameConstants
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MusicOnHold implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("type")
        private String type;

        @JsonProperty("value")
        private String value;
    }

    @Data
    @Accessors(chain = true)
    @FieldNameConstants
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ParallelRinging implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("activate")
        private Boolean activate;

        @JsonProperty("max_parallel_attempts")
        private Integer maxParallelAttempts;
    }

    @Data
    @Accessors(chain = true)
    @FieldNameConstants
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StartCallPlayback implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("playback_to")
        private String playbackTo;

        @JsonProperty("type")
        private String type;

        @JsonProperty("value")
        private String value;
    }
}
