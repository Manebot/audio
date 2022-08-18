package io.manebot.plugin.audio.mixer.input;

import io.manebot.plugin.audio.mixer.output.PipedMixerSink;
import io.manebot.virtual.Profiler;

import java.io.IOException;

public class PipedMixerChannel implements MixerChannel {
    private final PipedMixerSink parent;
    private final float[] buffer;
    private final Object lock = new Object();
    private final int bufferSize;
    private int position;

    public PipedMixerChannel(PipedMixerSink parent, int bufferSize) {
        this.parent = parent;
        this.bufferSize = bufferSize;
        this.buffer = new float[bufferSize];
    }

    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public boolean isPlaying() {
        return true;
    }

    @Override
    public int available() {
        return position;
    }

    public int availableInput() {
        return buffer.length - position;
    }

    @Override
    public int read(float[] buffer, int offs, int len) throws IOException {
        try (Profiler profiler = Profiler.region("pipe")) {
            synchronized (lock) {
                int copy = Math.min(len, position);

                if (copy > 0) {
                    System.arraycopy(this.buffer, 0, buffer, offs, copy);
                    System.arraycopy(this.buffer, copy, this.buffer, 0, this.buffer.length - copy);
                    position -= copy;
                }

                return copy;
            }
        }
    }

    @Override
    public int getSampleRate() {
        return (int) parent.getAudioFormat().getSampleRate();
    }

    @Override
    public int getChannels() {
        return parent.getAudioFormat().getChannels();
    }

    @Override
    public void close() throws Exception {
        synchronized (lock) {
            position = 0;

            lock.notifyAll();
        }

        for (int i = 0; i < buffer.length; i ++)
            buffer[i] = 0f;
    }

    public int write(float[] buffer, int len) {
        if (len <= 0) return 0;

        try (Profiler profiler = Profiler.region("pipe")) {
            synchronized (lock) {
                int n = Math.min(this.buffer.length - position, len);

                if (n <= 0) return 0;

                System.arraycopy(buffer, 0, this.buffer, position, n);

                this.position += n;

                return n;
            }
        }
    }

    @Override
    public String toString() {
        return "PipedChannel{" + parent.toString() + "}";
    }
}
