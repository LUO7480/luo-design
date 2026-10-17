package cn.studylock;

import java.time.Instant;
import java.time.ZoneId;

public final class RecoveryRules {
    private RecoveryRules() {}
    public static boolean mayReplace(boolean ready, boolean used, boolean enabled, boolean committed) {
        return !enabled && !committed && (!ready || used);
    }
    public static boolean challengeValid(long now, long elapsed, long issuedWall, long issuedElapsed, boolean sameBoot) {
        return sameBoot && now >= issuedWall && now - issuedWall < 900000
                && elapsed >= issuedElapsed && elapsed - issuedElapsed < 900000;
    }

    public static long cooldown(long now, long elapsed, long untilWall, long untilElapsed, boolean sameBoot) {
        return Math.max(0, Math.min(60000, sameBoot ? untilElapsed - elapsed : untilWall - now));
    }

    public static long tonightEnd(long now, ZoneId zone) {
        long end = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atTime(23, 0).atZone(zone).toInstant().toEpochMilli();
        return Math.max(now, end);
    }
}
