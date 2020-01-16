package io.manebot.plugin.audio.mixer.output;

/**
 * Represents a floating point PCM sample stream.
 */
public interface AudioConsumer extends AutoCloseable {
    
    /**
     * Writes a set of float samples, interleaved by channel, to the sink.
     * @param buffer Sample buffer
     * @param len Length of sample buffer to copy into the sink.
     */
    void write(float[] buffer, int len);

}