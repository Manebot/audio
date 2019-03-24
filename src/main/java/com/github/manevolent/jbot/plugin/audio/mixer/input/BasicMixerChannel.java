package com.github.manevolent.jbot.plugin.audio.mixer.input;

import java.io.IOException;

public class BasicMixerChannel implements MixerChannel {
    private final Object playingLock = new Object();
    private final Object priorityLock = new Object();

    private final AudioProvider provider;

    private volatile boolean priority = false;
    private volatile boolean playing = true;

    public BasicMixerChannel(AudioProvider provider) {
        this.provider = provider;
    }

    public void setPriority(boolean priority) {
        synchronized (priorityLock) {
            if (this.priority != priority) this.priority = priority;
        }
    }

    public void setPlaying(boolean playing) {
        synchronized (playingLock) {
            if (this.playing != playing) this.playing = playing;
        }
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    public void close() throws Exception {
        setPlaying(false);

        provider.close();
    }

    @Override
    public int available() {
        return provider.available();
    }

    @Override
    public int getSampleRate() {
        return provider.getSampleRate();
    }

    @Override
    public int getChannels() {
        return provider.getChannels();
    }

    @Override
    public int read(float[] buffer, int offs, int len) throws IOException {
        return provider.read(buffer, offs, len);
    }
}
