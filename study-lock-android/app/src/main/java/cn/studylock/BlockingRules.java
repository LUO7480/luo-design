package cn.studylock;

/** Pure decision shared by the overlay service and JVM checks. */
public final class BlockingRules {
    public static boolean cover(long end, long now, boolean consent, boolean phonePermission,
            boolean keyguard, boolean callSurface, boolean appVisible, boolean permittedPackage) {
        return end > now && consent && phonePermission && !keyguard && !callSurface
                && !appVisible && !permittedPackage;
    }
}
