package id.ruangangklung.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Preloaded real angklung recordings. Playback works offline. */
public final class SoundEngine {
    private static final int[] RESOURCES = {
            R.raw.angklung_g3, R.raw.angklung_a3, R.raw.angklung_as3, R.raw.angklung_b3,
            R.raw.angklung_c4, R.raw.angklung_d4, R.raw.angklung_e4, R.raw.angklung_f4,
            R.raw.angklung_fs4, R.raw.angklung_g4, R.raw.angklung_a4, R.raw.angklung_as4,
            R.raw.angklung_b4, R.raw.angklung_c5, R.raw.angklung_d5
    };
    private final SoundPool pool;
    private final int[] soundIds = new int[RESOURCES.length];
    private final Set<Integer> loaded = ConcurrentHashMap.newKeySet();
    private volatile float volume = .8f;
    private boolean released;

    public SoundEngine(Context context) {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(32).setAudioAttributes(attributes).build();
        pool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0) loaded.add(sampleId);
        });
        for (int i = 0; i < RESOURCES.length; i++) soundIds[i] = pool.load(context, RESOURCES[i], 1);
    }

    public synchronized boolean play(String note) {
        int index = NoteCatalog.indexOf(note);
        if (released || index < 0 || !loaded.contains(soundIds[index])) return false;
        float level = volume * volume;
        return pool.play(soundIds[index], level, level, 1, 0, 1f) != 0;
    }

    public void setVolume(int percent) {
        volume = Math.max(0, Math.min(100, percent)) / 100f;
    }

    public synchronized void close() {
        if (released) return;
        released = true;
        pool.release();
    }
}
