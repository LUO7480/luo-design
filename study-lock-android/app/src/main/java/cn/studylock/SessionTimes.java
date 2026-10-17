package cn.studylock;

/** Clock-independent expiry rules used by short trials and the release safeguard. */
public final class SessionTimes {
    private SessionTimes() {}

    public static long trialEnd(long now, long elapsed, long wallEnd, long elapsedEnd, boolean sameBoot) {
        if (!sameBoot || wallEnd <= now || elapsedEnd <= elapsed) return 0;
        return Math.min(wallEnd, now + elapsedEnd - elapsed);
    }

    public static boolean movedBackAfterDeadline(long now, long elapsed, long wallEnd, long elapsedEnd, boolean sameBoot) {
        return sameBoot && wallEnd > now && elapsedEnd <= elapsed;
    }

    /** Two-minute rehearsal: lock 60 seconds, rest 30 seconds, lock 30 seconds. */
    public static long trialLockEnd(long now, long elapsed, long wallEnd, long elapsedEnd, boolean sameBoot) {
        long end = trialEnd(now, elapsed, wallEnd, elapsedEnd, sameBoot);
        long left = end - now;
        if (end == 0 || left > 30000 && left <= 60000) return 0;
        return left > 60000 ? end - 60000 : end;
    }

    public static long trialNextBoundary(long now, long elapsed, long wallEnd, long elapsedEnd, boolean sameBoot) {
        long end = trialEnd(now, elapsed, wallEnd, elapsedEnd, sameBoot);
        long left = end - now;
        if (end == 0) return 0;
        return left > 60000 ? end - 60000 : left > 30000 ? end - 30000 : end;
    }
}
