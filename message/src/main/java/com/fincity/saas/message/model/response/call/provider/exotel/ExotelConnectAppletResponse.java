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
    private static final long serialVersionUID = 1L;

    @JsonProperty("fetch_after_attempt")
    private Boolean fetchAfterAttempt;

    @JsonProperty("destination")
    private Destination destination;

    @JsonProperty("outgoing_phone_number")
    private String outgoingPhoneNumber;

    @JsonProperty("record")
    private Boolean record;

    @JsonProperty("recording_channels")
    private String recordingChannels;

    @JsonProperty("max_ringing_duration")
    private Integer maxRingingDuration;

    @JsonProperty("max_conversation_duration")
    private Integer maxConversationDuration;

    @JsonProperty("music_on_hold")
    private MusicOnHold musicOnHold;

    @JsonProperty("parallel_ringing")
    private ParallelRinging parallelRinging;

    @JsonProperty("dial_passthru_event_url")
    private String dialPassthruEventUrl;

    @JsonProperty("start_call_playback")
    private StartCallPlayback startCallPlayback;

    /**
     * Destination class for the numbers to dial.
     */
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ExotelConnectAppletResponse response = new ExotelConnectAppletResponse();
        private Destination destination;
        private MusicOnHold musicOnHold;
        private ParallelRinging parallelRinging;
        private StartCallPlayback startCallPlayback;

        public Builder fetchAfterAttempt(Boolean fetchAfterAttempt) {
            response.setFetchAfterAttempt(fetchAfterAttempt);
            return this;
        }

        public Builder destinationNumbers(List<String> numbers) {
            if (destination == null) {
                destination = new Destination();
                response.setDestination(destination);
            }
            destination.setNumbers(numbers);
            return this;
        }

        public Builder outgoingPhoneNumber(String outgoingPhoneNumber) {
            response.setOutgoingPhoneNumber(outgoingPhoneNumber);
            return this;
        }

        public Builder record(Boolean record) {
            response.setRecord(record);
            return this;
        }

        public Builder recordingChannels(String recordingChannels) {
            response.setRecordingChannels(recordingChannels);
            return this;
        }

        public Builder maxRingingDuration(Integer maxRingingDuration) {
            response.setMaxRingingDuration(maxRingingDuration);
            return this;
        }

        public Builder maxConversationDuration(Integer maxConversationDuration) {
            response.setMaxConversationDuration(maxConversationDuration);
            return this;
        }

        public Builder musicOnHold(String type, String value) {
            musicOnHold = new MusicOnHold().setType(type);
            if (value != null) {
                musicOnHold.setValue(value);
            }
            response.setMusicOnHold(musicOnHold);
            return this;
        }

        public Builder parallelRinging(Boolean activate, Integer maxParallelAttempts) {
            parallelRinging = new ParallelRinging().setActivate(activate).setMaxParallelAttempts(maxParallelAttempts);
            response.setParallelRinging(parallelRinging);
            return this;
        }

        public Builder dialPassthruEventUrl(String dialPassthruEventUrl) {
            response.setDialPassthruEventUrl(dialPassthruEventUrl);
            return this;
        }

        public Builder startCallPlayback(String playbackTo, String type, String value) {
            startCallPlayback = new StartCallPlayback()
                    .setPlaybackTo(playbackTo)
                    .setType(type)
                    .setValue(value);
            response.setStartCallPlayback(startCallPlayback);
            return this;
        }

        public ExotelConnectAppletResponse build() {
            return response;
        }
    }
}
