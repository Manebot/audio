package io.manebot.plugin.audio.mixer.input;

import io.manebot.plugin.audio.resample.Resampler;
import io.manebot.plugin.audio.resample.ResamplerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.EOFException;
import java.io.IOException;

/**
 * Represents a floating point PCM sample stream.
 */
public interface AudioProvider extends AutoCloseable {

    /**
     * Gets count of available samples
     * @return samples available
     */
    int available();

    /**
     * Read samples.  This is a non-blocking operation.
     * @param buffer Sample buffer.
     * @param len Sample length to read.
     * @return Samples filled into the target buffer.
     * @throws IOException if there was a problem reading the stream
     * @throws EOFException if the end of the stream was reached
     */
    int read(float[] buffer, int offs, int len) throws IOException, EOFException;

    /**
     * Gets the sample rate of this provider.
     * @return sample rate.
     */
    int getSampleRate();

    /**
     * Gets the channel count of this provider.
     * @return channel count.
     */
    int getChannels();

    /**
     * Describes the format of this audio provider in the Java AudioFormat type.
     * @return AudioFormat instance corresponding to the format of this audio provider.
     */
    default AudioFormat getFormat() {
        return getFormat(getSampleRate(), getChannels());
    }

    static AudioFormat getFormat(int sampleRate, int channels) {
        return getFormat((float)sampleRate, channels);
    }

    static AudioFormat getFormat(float  sampleRate, int channels) {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_FLOAT,
                sampleRate,
                32,
                channels,
                4 * channels,
                sampleRate,
                false
        );
    }

}