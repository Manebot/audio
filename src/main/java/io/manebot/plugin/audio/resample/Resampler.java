package io.manebot.plugin.audio.resample;

import io.manebot.plugin.audio.AudioBuffer;

import javax.sound.sampled.AudioFormat;
import java.nio.*;
import java.util.function.*;

public abstract class Resampler implements AutoCloseable {
    private final AudioFormat inputFormat, outputFormat;

    protected Resampler(AudioFormat inputFormat, AudioFormat outputFormat) {
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
    }
    
    public double getScale() {
        return  ((inputFormat.getSampleSizeInBits() * inputFormat.getChannels() * inputFormat.getSampleRate()) /
                        (outputFormat.getSampleSizeInBits() * outputFormat.getChannels() * outputFormat.getSampleRate()));
    }

    public int getScaledBufferSize(int bufferSize) {
        return (int) Math.ceil(bufferSize * getScale());
    }

    public AudioFormat getInputFormat() {
        return inputFormat;
    }

    public AudioFormat getOutputFormat() {
        return outputFormat;
    }

    public int resample(AudioBuffer in, AudioBuffer out) {
        return resample(in::read, in.availableOutput(), out::write, out.availableInput());
    }
    
    public int resample(float[] in, int in_available, BiFunction<FloatBuffer, Integer, Integer> out, int out_available) {
        return resample((buffer, len) -> { buffer.put(in, 0, len); return len; }, in_available, out, out_available);
    }
    
    public int resample(float[] in, int in_available, float[] out, int out_available) {
        return resample((buffer, len) -> { buffer.put(in, 0, len); return len; }, in_available,
                        (buffer, len) -> { buffer.get(out, 0, len); return len; }, out_available);
    }
    
    public abstract int resample(BiFunction<FloatBuffer, Integer, Integer> in, int in_available,
                    BiFunction<FloatBuffer, Integer, Integer> out, int out_available);
    
    public int flush(AudioBuffer out) {
        return flush(out::write, out.availableInput());
    }
    
    public int flush(float[] out, int out_available) {
        return flush((buffer, len) -> { buffer.get(out, 0, len); return len; }, out_available);
    }
    
    public abstract int flush(BiFunction<FloatBuffer, Integer, Integer> out, int out_available);

    @Override
    public String toString() {
        return "Resampler{" + inputFormat + "->" + outputFormat + "}";
    }

}
