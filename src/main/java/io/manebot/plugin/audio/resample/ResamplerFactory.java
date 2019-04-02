package io.manebot.plugin.audio.resample;

import javax.sound.sampled.AudioFormat;

public interface ResamplerFactory {
    Resampler create(AudioFormat in, AudioFormat out, int bufferSize);
}
