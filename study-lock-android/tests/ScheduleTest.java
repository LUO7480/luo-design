import cn.studylock.Schedule;
import cn.studylock.SessionTimes;
import java.time.ZonedDateTime;
import java.time.ZoneId;

public final class ScheduleTest {
    private static int assertions;
    static long time(String value) { return ZonedDateTime.parse(value).toInstant().toEpochMilli(); }
    static void equal(long actual, long expected, String message) {
        assertions++;
        if (actual != expected) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long start = time("2026-10-17T19:00:00+08:00");
        long end = time("2026-10-17T23:00:00+08:00");
        long firstRest = time("2026-10-17T19:50:00+08:00");
        equal(Schedule.activeEnd(start - 1, zone), 0, "Before start must not lock");
        equal(Schedule.activeEnd(start, zone), firstRest, "First fifty-minute lock");
        equal(Schedule.activeEnd(firstRest - 1, zone), firstRest, "Last locked millisecond");
        equal(Schedule.activeEnd(firstRest, zone), 0, "Rest begins at minute fifty");
        equal(Schedule.activeEnd(end - 1, zone), 0, "Last ten minutes remain rest");
        equal(Schedule.activeEnd(end, zone), 0, "End excluded");
        equal(Schedule.nextBoundary(start - 1, zone), start, "Next start");
        equal(Schedule.nextBoundary(start, zone), firstRest, "Next rest start");
        equal(Schedule.nextBoundary(firstRest, zone), time("2026-10-17T20:00:00+08:00"), "Rest ends at next hour");
        equal(Schedule.activeEnd(time("2026-10-17T20:00:00+08:00"), zone), time("2026-10-17T20:50:00+08:00"), "Automatically relock");
        equal(Schedule.nextBoundary(time("2026-10-17T22:50:00+08:00"), zone), end, "Final rest ends the daily window");
        equal(Schedule.nextBoundary(end, zone), time("2026-10-18T19:00:00+08:00"), "Next day after end");
        equal(Schedule.activeEnd(time("2026-10-18T00:00:00+08:00"), zone), 0, "Midnight unlocked");
        // Verify every minute of two days, including both boundaries on both days.
        long first = time("2026-10-17T00:00:00+08:00");
        for (int minute = 0; minute < 2880; minute++) {
            long now = first + minute * 60000L;
            boolean expected = minute % 1440 >= 19 * 60 && minute % 1440 < 23 * 60 && minute % 60 < 50;
            equal(Schedule.activeEnd(now, zone) > 0 ? 1 : 0, expected ? 1 : 0, "Minute " + minute);
            if (Schedule.nextBoundary(now, zone) <= now) throw new AssertionError("Boundary must be in future");
        }
        ZoneId newYork = ZoneId.of("America/New_York");
        equal(Schedule.nextBoundary(time("2026-10-31T23:00:00-04:00"), newYork), time("2026-11-01T19:00:00-05:00"), "DST must use next local date");
        equal(SessionTimes.trialEnd(1000000, 5000, 1120000, 125000, true), 1120000, "Trial begins with two minutes");
        equal(SessionTimes.trialEnd(1120000, 125000, 1120000, 125000, true), 0, "Trial ends on exact boundary");
        equal(SessionTimes.trialEnd(900000, 125000, 1120000, 125000, true), 0, "Clock rollback must not extend trial");
        equal(SessionTimes.trialEnd(2000000, 6000, 1120000, 125000, true), 0, "Clock forward releases trial");
        equal(SessionTimes.trialEnd(1000000, 1000, 1120000, 125000, false), 0, "Reboot never restarts a trial");
        equal(SessionTimes.trialEnd(900000, 65000, 1120000, 125000, true), 960000, "Clock rollback bounds remaining trial duration");
        equal(SessionTimes.movedBackAfterDeadline(1000, 100, 2000, 100, true) ? 1 : 0, 1, "Release safeguard detects rollback");
        equal(SessionTimes.movedBackAfterDeadline(2000, 100, 2000, 100, true) ? 1 : 0, 0, "Normal expiry is not clock rollback");
        equal(SessionTimes.movedBackAfterDeadline(1000, 100, 2000, 100, false) ? 1 : 0, 0, "Elapsed deadline does not cross reboot");
        equal(SessionTimes.trialLockEnd(1000000, 5000, 1120000, 125000, true), 1060000, "Rehearsal first lock sixty seconds");
        equal(SessionTimes.trialLockEnd(1060000, 65000, 1120000, 125000, true), 0, "Rehearsal starts rest");
        equal(SessionTimes.trialNextBoundary(1060000, 65000, 1120000, 125000, true), 1090000, "Rehearsal rest thirty seconds");
        equal(SessionTimes.trialLockEnd(1090000, 95000, 1120000, 125000, true), 1120000, "Rehearsal relocks for thirty seconds");
        equal(SessionTimes.trialLockEnd(1120000, 125000, 1120000, 125000, true), 0, "Rehearsal fully ends");
        System.out.println("PASS: " + assertions + " assertions; daily boundaries, DST, trial expiry, reboot, and clock changes");
    }
}
