package io.manebot.plugin.audio.mixer.output;

import io.manebot.virtual.Profiler;

import javax.sound.sampled.AudioFormat;

public class RingBufferSink implements MixerSink {
    private final Object stateLock = new Object();
    private final float[] buffer;
    private final AudioFormat format;

    private boolean running;
    private long pos;

    private long overflows;

    public RingBufferSink(AudioFormat format, float seconds) {
        this.format = format;
        this.buffer = new float[(int) Math.ceil(seconds * format.getSampleRate()) * format.getChannels()];
    }

    public float[] getBuffer() {
        return buffer;
    }

    @Override
    public AudioFormat getAudioFormat() {
        return format;
    }

    @Override
    public void write(float[] buffer, int len) {
        try (Profiler profiler = Profiler.region("ringwrite")) {
            synchronized (this.buffer) {
                if (len > buffer.length) {
                    throw new ArrayIndexOutOfBoundsException("length > buffer size");
                }

                // Calculate how much to write
                int write = Math.min(len, this.buffer.length);

                if (len > this.buffer.length)
                    overflows++;

                // Left shift current buffer to accommodate the new samples
                int amount = this.buffer.length - write;
                if (amount > 0)
                    System.arraycopy(this.buffer, write, this.buffer, 0, amount);

                // Copy buffer
                System.arraycopy(
                        buffer, 0,
                        this.buffer, this.buffer.length - write, // copy to the right side of the ring buffer
                        write // length
                );

                // Offset position
                this.pos += write;
            }
        }
    }

    @Override
    public int availableInput() {
        return running ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean start() {
        synchronized (stateLock) {
            if (!running) {
                pos = 0;
                running = true;
                return true;
            } else
                return false;
        }
    }

    @Override
    public boolean stop() {
        synchronized (stateLock) {
            if (running) {
                running = false;
                return true;
            } else return false;
        }
    }

    @Override
    public int getBufferSize() {
        return buffer.length;
    }

    @Override
    public long getPosition() {
        return pos;
    }

    @Override
    public long getUnderflows() {
        return 0;
    }

    @Override
    public long getOverflows() {
        return overflows;
    }

    @Override
    public String toString() {
        return "RingBuffer[" + buffer.length + "]";
    }
}
