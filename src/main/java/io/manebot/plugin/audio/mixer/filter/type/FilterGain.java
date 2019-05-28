package io.manebot.plugin.audio.mixer.filter.type;

import io.manebot.plugin.audio.mixer.filter.AbstractFilter;
import io.manebot.plugin.audio.mixer.filter.Filter;
import io.manebot.plugin.audio.mixer.filter.SingleChannelFilter;

/**
 * Compresses an audio signal (analog).
 *
 * When Q is closer to 0, the signal volume is increased.
 * When Q is 1, the signal volume is not modified.
 * When Q is closer to infinity, the signal volume is reduced.
 */
public class FilterGain extends AbstractFilter implements SingleChannelFilter {
    private float q;

    public FilterGain(float sampleRate, float q) {
        super(sampleRate);

        this.q = q;
    }

    public void setQ(float q) {
        this.q = q;
    }

    public float getQ() {
        return q;
    }

    @Override
    public int process(float[] samples, int offs, int len) {
        for (int i = 0; i < len; i ++) {
            samples[i+offs] = samples[i+offs] * q;
        }

        return len;
    }
}
