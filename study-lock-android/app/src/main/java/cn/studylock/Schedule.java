package cn.studylock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Daily local-time schedule. The end boundary is exclusive. */
public final class Schedule {
    private Schedule() {}

    public static long activeEnd(long now, ZoneId zone) {
        ZonedDateTime time = Instant.ofEpochMilli(now).atZone(zone);
        LocalDate day = time.toLocalDate();
        long start = day.atTime(19, 0).atZone(zone).toInstant().toEpochMilli();
        long end = day.atTime(23, 0).atZone(zone).toInstant().toEpochMilli();
        return now >= start && now < end ? end : 0;
    }

    public static long nextBoundary(long now, ZoneId zone) {
        long end = activeEnd(now, zone);
        if (end > 0) return end;
        LocalDate day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        long start = day.atTime(19, 0).atZone(zone).toInstant().toEpochMilli();
        return start > now ? start : day.plusDays(1).atTime(19, 0).atZone(zone).toInstant().toEpochMilli();
    }
}
