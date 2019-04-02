package io.manebot.plugin.audio.mixer.output;

import io.manebot.plugin.audio.mixer.input.PipedMixerChannel;

import javax.sound.sampled.AudioFormat;

public class PipedMixerSink implements MixerSink {
    private final AudioFormat format;
    private final PipedMixerChannel channel;

    private volatile boolean running = false;
    private long underflowed = 0L;
    private long overflowed = 0L;
    private long position = 0L;

    public PipedMixerSink(AudioFormat format, int bufferSize) {
        this.channel = new PipedMixerChannel(this, bufferSize);
        this.format = format;
    }

    public PipedMixerChannel getPipe() {
        return channel;
    }

    @Override
    public AudioFormat getAudioFormat() {
        return format;
    }

    @Override
    public void write(float[] buffer, int len) {
        int ret = channel.write(buffer, len);

        if (ret <= 0) underflowed++;
        else if (ret < len) overflowed++;

        position += ret;
    }

    @Override
    public int availableInput() {
        return getPipe().availableInput();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean start() {
        running = true;
        return true;
    }

    @Override
    public boolean stop() {
        running = false;
        return true;
    }

    @Override
    public int getBufferSize() {
        return getPipe().getBufferSize();
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public long getUnderflows() {
        return underflowed;
    }

    @Override
    public long getOverflows() {
        return overflowed;
    }
}
