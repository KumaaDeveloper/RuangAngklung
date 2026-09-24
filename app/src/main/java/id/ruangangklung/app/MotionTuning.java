package id.ruangangklung.app;

/** Consistent motion thresholds and tempo across phones and settings screens. */
public final class MotionTuning {
    private MotionTuning() { }

    private static float fraction(int sensitivity) {
        return Math.max(0, Math.min(100, sensitivity)) / 100f;
    }

    /** Zero deliberately disables automatic motion playback; touch still works. */
    public static boolean enabled(int sensitivity) { return sensitivity > 0; }

    public static float gyroThreshold(int sensitivity) {
        return 7.3f - 5.8f * (float) Math.sqrt(fraction(sensitivity));
    }

    public static float accelerationThreshold(int sensitivity) {
        return 13.0f - 10.0f * (float) Math.sqrt(fraction(sensitivity));
    }

    public static long minimumGapNs(int sensitivity) {
        return 320_000_000L - Math.round(230_000_000L * fraction(sensitivity));
    }

    public static String settingLabel(int sensitivity) {
        if (!enabled(sensitivity)) return "Sensitivitas gerakan: 0% · sensor gerak mati";
        return "Sensitivitas gerakan: " + sensitivity + "% · jeda minimal "
                + minimumGapNs(sensitivity) / 1_000_000L + " ms";
    }
}
