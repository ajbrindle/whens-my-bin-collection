package com.sk7software.bincollection.util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class DateUtil {

    public static final DateTimeFormatter DEFAULT_DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd");
    public static final DateTimeFormatter SPOKEN_DATE_FORMAT = DateTimeFormat.forPattern("EEEEE, dd MMMMM");

    public static int calcNumberOfDays(DateTime date) {
        DateTime now = new DateTime().withZone(DateTimeZone.forID("Europe/London"));
        int days = Days.daysBetween(now.withTimeAtStartOfDay(), date.withTimeAtStartOfDay()).getDays();
        if (days < 0) days = 0;
        return days;
    }

    public static String getDayDescription(DateTime date) {
        int numDays = calcNumberOfDays(date);
        switch (numDays) {
            case 0:
                return "today";
            case 1:
                return "tomorrow";
            default:
                return "in " + numDays + " days, on " + SPOKEN_DATE_FORMAT.print(date);

        }
    }

}
