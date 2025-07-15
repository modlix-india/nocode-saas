package com.fincity.saas.message.enums.call.exotel;

import java.util.List;
import lombok.Getter;

public class ExotelOptions {

    @Getter
    public abstract static sealed class ExotelOption
            permits RecordingChannel,
                    RecordingFormat,
                    StartPlaybackTo,
                    StatusCallbackEvents,
                    StatusCallbackContentType {
        private final String exotelName;
        private final boolean isDefault;

        protected ExotelOption(String exotelName, boolean isDefault) {
            this.exotelName = exotelName;
            this.isDefault = isDefault;
        }

        public static <T extends ExotelOption> T getDefault(List<T> items) {
            for (T item : items) {
                if (item.isDefault()) {
                    return item;
                }
            }
            throw new IllegalArgumentException("No default value defined");
        }
    }

    public static final class RecordingChannel extends ExotelOption {
        public static final RecordingChannel SINGLE = new RecordingChannel("single", true);
        public static final RecordingChannel DUAL = new RecordingChannel("dual", false);
        public static final List<RecordingChannel> VALUES = List.of(SINGLE, DUAL);

        private RecordingChannel(String exotelName, boolean isDefault) {
            super(exotelName, isDefault);
        }

        public static String getDefault() {
            return ExotelOption.getDefault(VALUES).getExotelName();
        }
    }

    public static final class RecordingFormat extends ExotelOption {
        public static final RecordingFormat MP3 = new RecordingFormat("mp3", true);
        public static final RecordingFormat MP3_HQ = new RecordingFormat("mp3_hq", false);
        public static final List<RecordingFormat> VALUES = List.of(MP3, MP3_HQ);

        private RecordingFormat(String exotelName, boolean isDefault) {
            super(exotelName, isDefault);
        }

        public static String getDefault() {
            return ExotelOption.getDefault(VALUES).getExotelName();
        }
    }

    public static final class StartPlaybackTo extends ExotelOption {
        public static final StartPlaybackTo CALLEE = new StartPlaybackTo("callee", true);
        public static final StartPlaybackTo BOTH = new StartPlaybackTo("both", false);
        public static final List<StartPlaybackTo> VALUES = List.of(CALLEE, BOTH);

        private StartPlaybackTo(String exotelName, boolean isDefault) {
            super(exotelName, isDefault);
        }

        public static String getDefault() {
            return ExotelOption.getDefault(VALUES).getExotelName();
        }
    }

    public static final class StatusCallbackEvents extends ExotelOption {
        public static final StatusCallbackEvents TERMINAL = new StatusCallbackEvents("terminal", true);
        public static final StatusCallbackEvents ANSWERED = new StatusCallbackEvents("answered", false);
        public static final List<StatusCallbackEvents> VALUES = List.of(TERMINAL, ANSWERED);

        private StatusCallbackEvents(String exotelName, boolean isDefault) {
            super(exotelName, isDefault);
        }

        public static String getDefault() {
            return ExotelOption.getDefault(VALUES).getExotelName();
        }
    }

    public static final class StatusCallbackContentType extends ExotelOption {
        public static final StatusCallbackContentType FORM = new StatusCallbackContentType("multipart/form-data", true);
        public static final StatusCallbackContentType JSON = new StatusCallbackContentType("application/json", false);
        public static final List<StatusCallbackContentType> VALUES = List.of(FORM, JSON);

        private StatusCallbackContentType(String exotelName, boolean isDefault) {
            super(exotelName, isDefault);
        }

        public static String getDefault() {
            return ExotelOption.getDefault(VALUES).getExotelName();
        }
    }
}
