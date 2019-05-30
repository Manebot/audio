package io.manebot.plugin.audio.mixer.input;

import io.manebot.plugin.audio.AudioBuffer;
import io.manebot.plugin.audio.resample.Resampler;
import io.manebot.plugin.audio.resample.ResamplerFactory;

import java.io.EOFException;
import java.io.IOException;

public abstract class BufferedAudioProvider implements AudioProvider {
    private final int bufferSize;
    private final AudioBuffer buffer;

    protected BufferedAudioProvider(int bufferSize, AudioBuffer buffer) {
        this.bufferSize = bufferSize;
        this.buffer = buffer;
    }

    protected BufferedAudioProvider(int bufferSize) {
        this(bufferSize, new AudioBuffer(bufferSize));
    }

    @Override
    public int available() {
        return buffer.availableOutput();
    }

    /**
     * Advances playback when the buffer is too small. <b>fillBuffer</b> does <i>not</i> need to completely fill the
     * buffer every execution.  It actually does not need to fill the buffer at all every execution; instead, over
     * several executions, it should continue to write an arbitrary number of output samples into the AudioBuffer.
     *
     * Piecing these chunks together is done in the <b>read(float[], int, int)</b> method of BufferedAudioProvider.
     *
     * @throws IOException if a problem is encountered processing the buffer.
     * @throws EOFException if the end of the stream is reached.
     */
    protected abstract void fillBuffer() throws IOException, EOFException;

    protected final AudioBuffer getBuffer() {
        return buffer;
    }

    public final int getBufferSize() {
        return bufferSize;
    }

    @Override
    public int read(float[] buffer, int offs, int len) throws IOException, EOFException {
        int pos = 0;
        boolean eof = false;

        while (!eof && pos < len) {
            while (!eof && this.buffer.availableOutput() <= 0) {
                try {
                    fillBuffer();
                } catch (EOFException ex) {
                    // silently consume the eof; buffer may be filled partially still
                    eof = true;
                }
            }

            pos += this.buffer.mix(
                    buffer,
                    pos + offs,
                    Math.min(
                            len - pos,
                            this.buffer.availableOutput()
                    )
            );
        }

        // if we've reached the end of the file ('eof' flag set high) and 'pos' (read samples) is <= 0, we
        // are at the true EOF, and the upper layer should be notified.
        if (eof && pos <= 0) throw new EOFException();

        return pos;
    }

    public AudioProvider resample(Resampler resampler) {
        return new ResampledAudioProvider(this, getBufferSize(), resampler);
    }

    public AudioProvider resample(ResamplerFactory factory, int sampleRate, int channels) {
        return resample(factory.create(getFormat(), AudioProvider.getFormat(sampleRate, channels), getBufferSize()));
    }
}
