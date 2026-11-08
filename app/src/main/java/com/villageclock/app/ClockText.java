package com.villageclock.app;

import android.content.Context;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Device-local finish clocks, separate from elapsed d/h/m/s durations. */
final class ClockText {
    static final String PREF="clock-format";
    static String mode(Context c){String mode=Store.prefs(c).getString(PREF,"device");return mode.equals("12")||mode.equals("24")?mode:"device";}
    static String settingLabel(Context c){String mode=mode(c);return mode.equals("12")?"12-Hour":mode.equals("24")?"24-Hour":"Device Default";}
    static String at(Context c,long end,long now){return at(c,end,now,true);}
    static String at(Context c,long end,long now,boolean multiline){
        if(end<=0)return "";
        String mode=mode(c);boolean twentyFour=mode.equals("24")||mode.equals("device")&&android.text.format.DateFormat.is24HourFormat(c);
        Locale locale=c.getResources().getConfiguration().getLocales().get(0);ZoneId zone=ZoneId.systemDefault();
        ZonedDateTime finish=Instant.ofEpochMilli(end).atZone(zone);LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String clock=finish.format(DateTimeFormatter.ofPattern(twentyFour?"HH:mm":"h:mm a",locale));
        LocalDate day=finish.toLocalDate();if(day.equals(today))return clock;
        String date=day.equals(today.plusDays(1))?"Tomorrow":finish.format(DateTimeFormatter.ofPattern(day.getYear()==today.getYear()?"MMM d":"MMM d, yyyy",locale));
        return date+(multiline?"\n":" · ")+clock;
    }
    private ClockText(){}
}
