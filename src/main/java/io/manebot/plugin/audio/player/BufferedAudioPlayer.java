package io.manebot.plugin.audio.player;

import io.manebot.plugin.audio.AudioBuffer;
import io.manebot.user.User;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

public abstract class BufferedAudioPlayer extends AudioPlayer {
    private final int bufferSize;
    private final AudioBuffer buffer;

    public BufferedAudioPlayer(Type type, User owner, AudioFormat outputFormat, int bufferSize) {
        super(type, owner, outputFormat);

        this.bufferSize = bufferSize;
        this.buffer = new AudioBuffer(bufferSize);
    }

    @Override
    public int available() {
        return isPlaying() ? buffer.getBufferSize() : buffer.availableOutput();
    }

    /**
     * Advances playback when the buffer is too small.
     * @return true if playback should continue, false otherwise.
     */
    protected abstract boolean processBuffer() throws IOException;

    protected final AudioBuffer getBuffer() {
        return buffer;
    }

    public final int getBufferSize() {
        return bufferSize;
    }

    @Override
    public int read(float[] buffer, int offs, int len) throws IOException {
        int pos = 0;
        boolean eof = false;
        while (!eof && pos < len) {
            while (!eof && this.buffer.availableOutput() <= 0) {
                eof = !processBuffer();
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

        return pos;
    }
}
