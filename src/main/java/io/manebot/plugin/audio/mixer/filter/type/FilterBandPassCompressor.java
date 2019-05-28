package io.manebot.plugin.audio.mixer.filter.type;

import io.manebot.plugin.audio.mixer.filter.AbstractFilter;
import io.manebot.plugin.audio.mixer.filter.SingleChannelFilter;
import io.manebot.plugin.audio.mixer.filter.SoftFilter;

public class FilterBandPassCompressor extends AbstractFilter implements SingleChannelFilter {
    private final SoftFilter softFilter;
    private final float wet, dry;
    private final float q;
    private float[] buffer;

    public FilterBandPassCompressor(float sampleRate, SoftFilter softFilter, float wet, float dry, float q) {
        super(sampleRate);

        this.softFilter = softFilter;
        this.wet = wet;
        this.dry = dry;
        this.q = q;
    }

    @Override
    public int process(float[] samples, int offs, int len) {
        if (buffer == null || buffer.length < len) buffer = new float[len];
        System.arraycopy(samples, offs, buffer, 0, len);

        softFilter.processAudio(buffer, len);

        for (int i = 0; i < len; i ++) {
            if (buffer[i] < 0)
                buffer[i] = 0f - (float) Math.pow(Math.abs(buffer[i]), q);
            else
                buffer[i] = (float) Math.pow(Math.abs(buffer[i]), q);
        }

        for (int i = 0; i < len; i ++) {
            samples[i+offs] = (samples[i+offs] * dry) + (buffer[i] * wet);
        }

        return len;
    }

    @Override
    public void reset() {
        softFilter.reset();

        for (int i = 0; i < buffer.length; i ++)
            buffer[i] = 0f;
    }
}
