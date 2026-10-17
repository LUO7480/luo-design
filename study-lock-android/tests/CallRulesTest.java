import cn.studylock.BlockingRules;
import cn.studylock.CallRules;

public final class CallRulesTest {
    private static int checks;
    static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        check(CallRules.allowed(CallRules.WECHAT, CallRules.WECHAT_VIDEO, false), "Known WeChat call screen allowed without granting full WeChat access");
        check(!CallRules.allowed(CallRules.WECHAT, "com.tencent.mm.ui.LauncherUI", false), "WeChat chats blocked");
        check(!CallRules.allowed(CallRules.WECHAT, "com.tencent.mm.ui.chatting.ChattingUI", false), "Conversation blocked");
        check(!CallRules.allowed(CallRules.WECHAT, "com.tencent.mm.plugin.voip.ui.RepairerVoipQoSUI", false), "Unrelated VoIP settings blocked");
        check(!CallRules.allowed(CallRules.WECHAT, CallRules.WECHAT_VIDEO + "Other", false), "Prefix lookalike blocked");
        check(!CallRules.allowed("other.package", CallRules.WECHAT_VIDEO, false), "Different package cannot impersonate call screen");
        check(!CallRules.allowed(CallRules.WECHAT, "", true), "Cellular call does not unlock all WeChat screens");
        check(CallRules.allowed("com.android.systemui", "", true), "System incoming call prompt allowed during real phone call");
        check(!CallRules.allowed("com.android.systemui", "", false), "System UI alone does not release app overlay");
        for (String packageName : new String[]{"com.miui.home", "com.android.launcher", "com.tencent.mm", "other.app"}) {
            boolean callSurface = CallRules.allowed(packageName, "", true);
            check(BlockingRules.cover(200, 100, true, true, false, callSurface, false, false), "Home and other apps covered even during call: " + packageName);
        }
        check(BlockingRules.cover(200, 100, true, true, false,
                CallRules.allowed(CallRules.WECHAT, "com.tencent.mm.ui.LauncherUI", false), false, false), "Leaving WeChat call restores overlay");
        check(!BlockingRules.cover(0, 100, true, true, false, false, false, false), "Rest and expiry remove overlay");
        System.out.println("PASS: " + checks + " call-surface restrictions; WeChat chat and home stay covered");
    }
}
