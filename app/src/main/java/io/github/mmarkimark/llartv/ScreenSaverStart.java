package io.github.mmarkimark.llartv;

/**
 * Whether the settings menu may start the screensaver now, decided from the two secure settings Android
 * reads when it dreams. Pure, so the JVM tests cover it; SettingsActivity reads the settings and launches.
 *
 * The start itself goes through SystemUI's exported Somnambulator activity (the "Start now" entry of
 * AOSP), which asks the dream manager to dream with whatever screensaver_components names. That is a
 * platform component, not a public API: a TV may not ship it, so the caller resolves it and catches the
 * failures. Nothing here writes a setting, so the inactivity timeout and the chosen screensaver stay as they are.
 */
final class ScreenSaverStart {
    private ScreenSaverStart() {}

    static final String LAUNCHER_PACKAGE = "com.android.systemui";
    static final String LAUNCHER_CLASS = "com.android.systemui.Somnambulator";
    /** Settings.Secure keys, hidden in the SDK: the same names scripts/install.sh writes over adb. */
    static final String COMPONENTS_KEY = "screensaver_components", ENABLED_KEY = "screensaver_enabled";

    static final String PKG = "io.github.mmarkimark.llartv", DREAM_CLASS = PKG + ".BingDream";

    enum Check {
        READY,          // LlarTV is the screensaver Android would start
        NOT_SELECTED,   // another screensaver (or the system default) would start instead
        DISABLED        // the screensaver is switched off; asking to dream could put the TV to sleep instead
    }

    /**
     * @param components the screensaver_components value (comma-separated; Android starts the first one)
     * @param enabled    the screensaver_enabled value; null (unset) follows the system default, which is on
     */
    static Check check(String components, String enabled) {
        if (!isOwnDream(first(components))) return Check.NOT_SELECTED;
        if (enabled != null && enabled.trim().equals("0")) return Check.DISABLED;
        return Check.READY;
    }

    /** "pkg/.BingDream" or "pkg/pkg.BingDream", the two forms a flattened ComponentName takes. */
    static boolean isOwnDream(String flat) {
        if (flat == null) return false;
        String f = flat.trim();
        int slash = f.indexOf('/');
        if (slash < 0 || !f.substring(0, slash).equals(PKG)) return false;
        String cls = f.substring(slash + 1);
        if (cls.startsWith(".")) cls = PKG + cls;
        return cls.equals(DREAM_CLASS);
    }

    static String first(String components) {
        if (components == null) return null;
        int comma = components.indexOf(',');
        return comma < 0 ? components : components.substring(0, comma);
    }
}
