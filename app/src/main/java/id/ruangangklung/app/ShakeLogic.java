package id.ruangangklung.app;

/** Detects distinct swings, including quick reversals without a full pause. */
public final class ShakeLogic {
    private float threshold;
    private boolean armed = true;
    private long lastPlayedNs;
    private float lastX, lastY, lastZ;

    public ShakeLogic(float threshold) { this.threshold = threshold; }

    public void setThreshold(float threshold) { this.threshold = threshold; }

    public boolean accept(float x, float y, float z, long timestampNs) {
        return accept(x, y, z, timestampNs, true);
    }

    /** Continue observing release and direction while another sensor is cooling down. */
    public boolean accept(float x, float y, float z, long timestampNs, boolean canTrigger) {
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        if (magnitude < threshold * .65f) armed = true;
        float dot = x * lastX + y * lastY + z * lastZ;
        if (magnitude >= threshold && dot < -magnitude * .35f) armed = true;
        long gap = magnitude >= threshold * 1.8f ? 75_000_000L : 100_000_000L;
        if (canTrigger && armed && magnitude >= threshold && timestampNs - lastPlayedNs >= gap) {
            lastPlayedNs = timestampNs;
            armed = false;
            lastX = x / magnitude;
            lastY = y / magnitude;
            lastZ = z / magnitude;
            return true;
        }
        return false;
    }

    public void reset() {
        armed = true;
        lastPlayedNs = 0L;
        lastX = lastY = lastZ = 0f;
    }
}
