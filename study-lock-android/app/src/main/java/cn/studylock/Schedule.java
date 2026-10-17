package cn.studylock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Daily local-time schedule, with the last ten minutes of each hour reserved for rest. */
public final class Schedule {
    private Schedule() {}

    public static long windowEnd(long now, ZoneId zone) {
        ZonedDateTime time = Instant.ofEpochMilli(now).atZone(zone);
        LocalDate day = time.toLocalDate();
        long start = day.atTime(19, 0).atZone(zone).toInstant().toEpochMilli();
        long end = day.atTime(23, 0).atZone(zone).toInstant().toEpochMilli();
        return now >= start && now < end ? end : 0;
    }

    public static long activeEnd(long now, ZoneId zone) {
        if (windowEnd(now, zone) == 0) return 0;
        ZonedDateTime time = Instant.ofEpochMilli(now).atZone(zone);
        return time.getMinute() < 50 ? time.withMinute(50).withSecond(0).withNano(0).toInstant().toEpochMilli() : 0;
    }

    public static boolean resting(long now, ZoneId zone) {
        return windowEnd(now, zone) > 0 && activeEnd(now, zone) == 0;
    }

    public static long nextBoundary(long now, ZoneId zone) {
        long end = activeEnd(now, zone);
        if (end > 0) return end;
        if (resting(now, zone)) {
            return Instant.ofEpochMilli(now).atZone(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
        }
        LocalDate day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        long start = day.atTime(19, 0).atZone(zone).toInstant().toEpochMilli();
        return start > now ? start : day.plusDays(1).atTime(19, 0).atZone(zone).toInstant().toEpochMilli();
    }
}
