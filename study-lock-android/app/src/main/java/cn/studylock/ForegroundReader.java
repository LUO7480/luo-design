package cn.studylock;

import android.app.usage.UsageEvents;
import android.app.usage.UsageEventsQuery;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;

final class ForegroundReader {
    final ForegroundTracker surface = new ForegroundTracker();
    private long cursor = -1;

    void refresh(Context context) {
        long now = System.currentTimeMillis();
        if (cursor < 0 || now < cursor) { surface.reset(); cursor = Math.max(0, now - 86400000); }
        UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
        long begin = Math.max(0, cursor - 1000);
        UsageEvents events = Build.VERSION.SDK_INT >= 35
                ? manager.queryEvents(new UsageEventsQuery.Builder(begin, now + 1).setEventTypes(1, 2, 23).build())
                : manager.queryEvents(begin, now + 1);
        if (events == null) { surface.clearSurface(); return; }
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            surface.accept(event.getEventType(), event.getTimeStamp(), event.getPackageName(), event.getClassName());
        }
        cursor = now;
    }
}
