import cn.studylock.BlockingRules;

public final class BlockingRulesTest {
    private static int checks;
    private static void expect(String name, boolean expected, long end, long now, boolean consent,
            boolean phone, boolean keyguard, boolean call, boolean own, boolean allowed) {
        if (BlockingRules.cover(end, now, consent, phone, keyguard, call, own, allowed) != expected) throw new AssertionError(name);
        checks++;
    }
    public static void main(String[] args) {
        expect("ordinary app during lesson", true, 200, 100, true, true, false, false, false, false);
        expect("home during lesson", true, 101, 100, true, true, false, false, false, false);
        expect("exact break boundary releases", false, 100, 100, true, true, false, false, false, false);
        expect("late callback releases", false, 90, 100, true, true, false, false, false, false);
        expect("plan off", false, 0, 100, true, true, false, false, false, false);
        expect("declined disclosure", false, 200, 100, false, true, false, false, false, false);
        expect("phone permission revoked", false, 200, 100, true, false, false, false, false, false);
        expect("system credential screen stays usable", false, 200, 100, true, true, true, false, false, false);
        expect("incoming or active call takes priority", false, 200, 100, true, true, false, true, false, false);
        expect("offline recovery keypad stays usable", false, 200, 100, true, true, false, false, true, false);
        expect("dialer and permission settings stay usable", false, 200, 100, true, true, false, false, false, true);
        expect("hangup restores lesson", true, 200, 101, true, true, false, false, false, false);
        expect("rest stays free even during call", false, 0, 100, true, true, false, true, false, true);
        System.out.println("PASS: " + checks + " consumer blocking and phone-priority checks");
    }
}
