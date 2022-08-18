package io.manebot.plugin.audio.mixer.input;

import io.manebot.plugin.audio.mixer.filter.SingleChannelFilter;

import java.io.EOFException;
import java.io.IOException;

public class FilteredMixerChannel implements MixerChannel {
    private final MixerChannel channel;
    private final SingleChannelFilter filter;

    public FilteredMixerChannel(MixerChannel channel, SingleChannelFilter filter) {
        this.channel = channel;
        this.filter = filter;
    }

    @Override
    public int available() {
        return channel.available();
    }

    @Override
    public int read(float[] buffer, int offs, int len) throws IOException, EOFException {
        int num = channel.read(buffer, offs, len);
        int filtered = filter.process(buffer, offs, num);
        assert filtered == num;
        return num;
    }

    @Override
    public int getSampleRate() {
        return channel.getSampleRate();
    }

    @Override
    public int getChannels() {
        return channel.getChannels();
    }

    @Override
    public boolean isPlaying() {
        return channel.isPlaying();
    }

    @Override
    public void close() throws Exception {
        channel.close();
    }
}
