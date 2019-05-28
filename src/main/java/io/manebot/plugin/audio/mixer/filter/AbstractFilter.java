package io.manebot.plugin.audio.mixer.filter;

public abstract class AbstractFilter implements Filter {
    private final float sampleRate;

    public AbstractFilter(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    @Override
    public float getSampleRate() {
        return sampleRate;
    }
}
