import cn.studylock.ForegroundTracker;
import cn.studylock.CallRules;

public final class ForegroundTrackerTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static boolean call(ForegroundTracker tracker) { return CallRules.allowed(tracker.packageName(), tracker.className(), false); }
    public static void main(String[] args) {
        ForegroundTracker tracker = new ForegroundTracker();
        check(!call(tracker), "Unknown foreground never grants a call exception");
        tracker.accept(1, 100, CallRules.WECHAT, CallRules.WECHAT_VIDEO);
        check(call(tracker), "Actual resumed call activity grants its exception");
        tracker.accept(1, 99, CallRules.WECHAT, "com.tencent.mm.ui.LauncherUI");
        check(call(tracker), "Older overlapping history cannot replace the latest activity");
        tracker.accept(2, 101, CallRules.WECHAT, CallRules.WECHAT_VIDEO);
        check(!call(tracker), "Paused call is no longer exempt");
        tracker.accept(1, 101, CallRules.WECHAT, "com.tencent.mm.ui.LauncherUI");
        check(!call(tracker) && tracker.className().endsWith("LauncherUI"), "Chat resume at the same timestamp stays blocked");
        tracker.accept(2, 101, CallRules.WECHAT, CallRules.WECHAT_VIDEO);
        check(tracker.className().endsWith("LauncherUI"), "Replayed pause cannot erase the new activity");
        tracker.accept(1, 102, CallRules.WECHAT, CallRules.WECHAT_VIDEO);
        tracker.accept(23, 103, "other.app", "Other");
        check(call(tracker), "Unrelated stopped activity cannot change the current call");
        tracker.clearSurface();
        tracker.accept(1, 102, CallRules.WECHAT, CallRules.WECHAT_VIDEO);
        check(!call(tracker), "Screen reset does not replay a stale call exemption");
        tracker.accept(1, 104, "com.miui.home", "Home");
        check(!call(tracker), "Home after call is blocked");
        tracker.accept(1, 105, CallRules.WECHAT, CallRules.WECHAT_VIDEO);
        tracker.accept(1, 106, CallRules.WECHAT, null);
        check(!call(tracker), "Missing activity identity fails closed");
        tracker.reset();
        tracker.accept(1, 5, CallRules.WECHAT, CallRules.WECHAT_VIDEO);
        check(call(tracker), "Clock reset can start a fresh event timeline");
        tracker.accept(23, 6, CallRules.WECHAT, CallRules.WECHAT_VIDEO);
        check(!call(tracker), "Stopped call cannot leave a stale exemption");
        System.out.println("PASS: " + checks + " foreground lifecycle and event replay checks");
    }
}
