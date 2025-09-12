package com.modlix.saas.commons2.mq.events;

public class EventNames {

    public static final String CLIENT_CREATED = "CLIENT_CREATED";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String CLIENT_REGISTERED = "CLIENT_REGISTERED";
    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String USER_PASSWORD_CHANGED = "USER_$_CHANGED";
    public static final String USER_PASSWORD_RESET_DONE = "USER_$_RESET_DONE";
    public static final String USER_RESET_PASSWORD_REQUEST = "USER_RESET_$_REQUEST";
    public static final String USER_CODE_GENERATION = "USER_CODE_GENERATION";
    public static final String USER_OTP_GENERATE = "USER_OTP_GENERATE";

    public static String getEventName(String eventName, Object... args) {
        if (args == null || args.length == 0) {
            return eventName;
        }

        String result = eventName;
        for (Object arg : args) {
            if (arg != null) {
                result = result.replaceFirst("\\$_", arg.toString() + "_");
            }
        }
        return result;
    }

    private EventNames() {
    }
}
