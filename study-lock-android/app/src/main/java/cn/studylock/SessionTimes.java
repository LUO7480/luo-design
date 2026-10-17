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
}
