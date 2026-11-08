package com.villageclock.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class DurationTextTest {
    @Test public void displaysAllFourUnitsIncludingSeconds(){
        assertEquals("1d 12h 18m 56s", DurationText.seconds(86400+12*3600+18*60+56));
        assertEquals("1d 7h 48m 52s", DurationText.seconds(31*3600+48*60+52));
    }
    @Test public void unitBoundariesRollOverWithoutGrowingHours(){
        assertEquals("0d 23h 59m 59s",DurationText.seconds(86399));
        assertEquals("1d 0h 0m 0s",DurationText.seconds(86400));
        assertEquals("0d 1h 0m 0s",DurationText.seconds(3600));
        assertEquals("0d 0h 1m 0s",DurationText.seconds(60));
    }
    @Test public void clampsExpiredValuesAndRoundsUpPartialSeconds(){
        assertEquals("0d 0h 0m 0s",DurationText.seconds(-1));
        assertEquals("0d 0h 0m 0s",DurationText.remaining(1000,2000));
        assertEquals("0d 0h 0m 1s",DurationText.remaining(1001,1000));
        assertEquals("0d 0h 0m 1s",DurationText.remaining(2000,1000));
        assertEquals("0d 0h 0m 2s",DurationText.remaining(2001,1000));
    }
    @Test public void largeDurationsDoNotWrapAtOneDay(){assertEquals("365d 0h 0m 0s",DurationText.seconds(365L*86400));}
}
