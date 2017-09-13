package com.sk7software.bincollection.model;

import com.sk7software.bincollection.util.DateUtil;
import org.joda.time.DateTime;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DateUtilTest {

    @Test
    public void testToday() {
        DateTime d1 = new DateTime().withTimeAtStartOfDay();
        assertEquals(DateUtil.calcNumberOfDays(d1), 0);
        assertEquals(DateUtil.getDayDescription(d1), "today");
    }

    @Test
    public void testTomorrow() {
        DateTime d1 = new DateTime().withTimeAtStartOfDay().plusDays(1);
        assertEquals(DateUtil.calcNumberOfDays(d1), 1);
        assertEquals(DateUtil.getDayDescription(d1), "tomorrow");
    }

    @Test
    public void test4Days() {
        DateTime d1 = new DateTime().withTimeAtStartOfDay().plusDays(4);
        assertEquals(DateUtil.calcNumberOfDays(d1), 4);
        assertTrue(DateUtil.getDayDescription(d1).indexOf("in 4 days") >= 0);
    }

}
