package cn.studylock;

/** Exact call surfaces; never grants the entire WeChat package an exemption. */
public final class CallRules {
    public static final String WECHAT = "com.tencent.mm";
    // Confirmed in the manifest of Tencent's official Android 8.0.79 APK.
    public static final String WECHAT_VIDEO = "com.tencent.mm.plugin.voip.ui.VideoActivity";

    public static boolean allowed(String packageName, String className, boolean cellularCall) {
        return WECHAT.equals(packageName) && WECHAT_VIDEO.equals(className)
                || cellularCall && "com.android.systemui".equals(packageName);
    }
}
