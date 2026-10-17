package cn.studylock;

import java.util.HashSet;
import java.util.Set;

/** Reduces activity lifecycle metadata without keeping an app-usage history. */
public final class ForegroundTracker {
    private long latest = -1;
    private final Set<String> seenAtLatest = new HashSet<>();
    private String packageName = "";
    private String className = "";

    public void accept(int type, long time, String name, String activity) {
        if (time < latest || type != 1 && type != 2 && type != 23) return;
        if (time > latest) { latest = time; seenAtLatest.clear(); }
        if (name == null || activity == null) { clearSurface(); return; }
        if (!seenAtLatest.add(type + "|" + name + "|" + activity)) return;
        if (type == 1) { packageName = name; className = activity; }
        else if (packageName.equals(name) && className.equals(activity)) clearSurface();
    }

    public String packageName() { return packageName; }
    public String className() { return className; }
    public void clearSurface() { packageName = ""; className = ""; }
    public void reset() { latest = -1; seenAtLatest.clear(); clearSurface(); }
}
