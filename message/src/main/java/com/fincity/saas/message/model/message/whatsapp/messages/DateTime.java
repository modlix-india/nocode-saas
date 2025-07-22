package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.CalendarType;

@JsonInclude(Include.NON_NULL)
public class DateTime {

    @JsonProperty("fallback_value")
    private String fallbackValue;

    @JsonProperty("calendar")
    private CalendarType calendar;

    @JsonProperty("month")
    private Integer month;

    @JsonProperty("hour")
    private Integer hour;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("day_of_month")
    private Integer dayOfMonth;

    @JsonProperty("day_of_week")
    private Integer dayOfWeek;

    @JsonProperty("minute")
    private Integer minute;

    public String getFallbackValue() {
        return fallbackValue;
    }

    public DateTime setFallbackValue(String fallbackValue) {
        this.fallbackValue = fallbackValue;
        return this;
    }

    public CalendarType getCalendar() {
        return calendar;
    }

    public DateTime setCalendar(CalendarType calendar) {
        this.calendar = calendar;
        return this;
    }

    public Integer getMonth() {
        return month;
    }

    public DateTime setMonth(Integer month) {
        this.month = month;
        return this;
    }

    public Integer getHour() {
        return hour;
    }

    public DateTime setHour(Integer hour) {
        this.hour = hour;
        return this;
    }

    public Integer getYear() {
        return year;
    }

    public DateTime setYear(Integer year) {
        this.year = year;
        return this;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public DateTime setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
        return this;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public DateTime setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
        return this;
    }

    public Integer getMinute() {
        return minute;
    }

    public DateTime setMinute(Integer minute) {
        this.minute = minute;
        return this;
    }
}
