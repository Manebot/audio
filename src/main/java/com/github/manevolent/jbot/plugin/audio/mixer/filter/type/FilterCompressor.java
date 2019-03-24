package com.github.manevolent.jbot.plugin.audio.mixer.filter.type;

import com.github.manevolent.jbot.plugin.audio.mixer.filter.MixerFilter;

/**
 * Compresses an audio signal (analog).
 *
 * When Q is closer to 0, the signal volume is increased.
 * When Q is 1, the signal volume is not modified.
 * When Q is closer to infinity, the signal volume is reduced.
 */
public class FilterCompressor implements MixerFilter {
    private final float iratio, thresh, ratio, knee, kneeL, kneeR, mul;

    public FilterCompressor(float thresh, float ratio, float knee) {
        this.thresh = thresh;
        this.ratio = ratio;
        this.iratio = 1f/ratio;
        this.knee = knee;
        this.kneeL = thresh - knee/2;
        this.kneeR = thresh + knee/2;
        this.mul = 1f / ((kneeL + knee*0.5f) + (1f - (kneeL + 0.5f*knee)) * iratio);
    }

    private static float spline2(float mu, float dv1, float dv2) {
        return mu*dv1 + mu*mu*0.5f*(dv2-dv1);
    }

    @Override
    public int process(float[] samples, int offs, int len) {
        float value;
        for (int i = 0; i < len; i ++) {
            value = Math.abs(samples[i]);

            // Process knee
            if (value >= kneeR)
                value = (kneeL + knee*0.5f) + (value - (kneeL + 0.5f*knee)) * iratio;
            else if (value > kneeL && value < kneeR)
                value = kneeL + spline2((value-kneeL)/knee,1f,iratio)*knee;

            value *= mul;

            // Re-pack sample into new output.
            if (samples[i] < 0)
                samples[i] = 0f - value; // negative value processing
            else
                samples[i] = value;
        }

        return len;
    }

    @Override
    public void reset() {

    }
}
