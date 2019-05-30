package io.manebot.plugin.audio.mixer;

import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.mixer.filter.Filter;
import io.manebot.plugin.audio.mixer.filter.MultiChannelFilter;
import io.manebot.plugin.audio.mixer.input.MixerChannel;
import io.manebot.plugin.audio.mixer.output.MixerSink;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BufferedMixer extends AbstractMixer {
    private final float[] buffer, mixBuffer;
    private final float[][] filterBuffer;

    private boolean filtering = true;
    private long position = 0L;

    public BufferedMixer(String id,
                         MixerRegistrant registrant,
                         int bufferSize,
                         float audioSampleRate,
                         int audioChannels) {
        super(id, registrant, bufferSize, audioSampleRate, audioChannels);

        this.buffer = new float[bufferSize];
        this.mixBuffer = new float[bufferSize];
        this.filterBuffer = new float[audioChannels][];
        for (int ch = 0; ch < audioChannels; ch ++)
            this.filterBuffer[ch] = new float[bufferSize / audioChannels];
    }

    @Override
    public boolean isFiltering() {
        return filtering;
    }

    @Override
    public void setFiltering(boolean enable) {
        this.filtering = enable;
        if (!enable) getFilters().forEach(Filter::reset);
    }

    @Override
    public boolean processBuffer() {
        if (!isPlaying())
            return false;

        // Plan for mixer input, ensuring the available sample count doesn't overflow the mixer
        int len = Math.min(buffer.length, available());

        // If no samples are ready, we don't bother with this, but we do signal that we must
        // continue playing samples.
        if (len > 0) {
            if (len > buffer.length)
                throw new ArrayIndexOutOfBoundsException(len + " > " + buffer.length);

            // Reset buffers
            for (int i = 0; i < len; i++) buffer[i] = 0f;

            Iterator<MixerChannel> channelIterator = getChannels().iterator();
            MixerChannel channel;
            while (channelIterator.hasNext()) {
                channel = channelIterator.next();
                if (channel == null) continue;

                try {
                    // Remove if the player is complete, otherwise mix
                    if (!channel.isPlaying()) {
                        removeChannel(channel);
                    } else {
                        // Reset mixing buffer
                        for (int i = 0; i < len; i++) mixBuffer[i] = 0f;

                        // Read samples from channel
                        int read = channel.read(mixBuffer, 0, len);

                        // Perform actual mixing
                        for (int i = 0; i < read; i++)
                            buffer[i] += mixBuffer[i];
                    }
                } catch (Throwable e) {
                    Logger.getGlobal().log(Level.SEVERE, "Problem playing audio on channel", e);
                    removeChannel(channel);
                }
            }

            // Manipulate audio based on filters
            if (filtering) {
                int channels = getAudioChannels();
                int samplesPerChannel = len / channels;
                for (int ch = 0; ch < channels; ch++) {
                    for (int smp = 0; smp < samplesPerChannel; smp++) {
                        filterBuffer[ch][smp] = buffer[(smp * channels) + ch];
                    }
                }

                getFilters().forEach(filter -> filter.process(filterBuffer, 0, samplesPerChannel));

                for (int ch = 0; ch < channels; ch++) {
                    for (int smp = 0; smp < samplesPerChannel; smp++) {
                        buffer[(smp * channels) + ch] = filterBuffer[ch][smp];
                    }
                }
            }

            // Write to sinks (only those that are running and can accept these samples, though)
            // Note that available() will limit "len" to the sink's availability
            for (MixerSink sink : getSinks())
                if (sink.isRunning() && sink.availableInput() >= len) sink.write(buffer, len);

            position += len;
        }

        // Kill the mixer, ensure it stops if necessary after we've processed all the buffers/channels
        //
        getChannels().stream().filter(x -> !x.isPlaying()).forEach(this::removeChannel);

        // Find if the mixer is still playing
        return isPlaying();
    }

    @Override
    public float getPositionInSeconds() {
        return (float)position / (float)(getAudioChannels() * getAudioSampleRate());
    }

    public static class Builder implements Mixer.Builder {
        private final Audio audio;
        private final String id;

        private final Collection<MixerSink> sinks = new LinkedList<>();
        private final Collection<Function<Mixer, MultiChannelFilter>> filters = new LinkedList<>();

        private MixerRegistrant registrant;
        private Float bufferTime;
        private Float sampleRate;
        private Integer channels;

        public Builder(Audio audio, String id) {
            this.audio = audio;
            this.id = id;
        }

        @Override
        public Audio getAudio() {
            return audio;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Builder setRegistrant(MixerRegistrant registrant) {
            this.registrant = registrant;
            return this;
        }

        @Override
        public Builder setBufferTime(float seconds) {
            bufferTime = seconds;
            return this;
        }

        @Override
        public Builder setFormat(float sampleRate, int channels) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            return this;
        }

        @Override
        public float getSampleRate() {
            return sampleRate;
        }

        @Override
        public int getChannels() {
            return channels;
        }

        @Override
        public Builder addSink(MixerSink sink) {
            sinks.add(sink);
            return this;
        }

        @Override
        public Mixer.Builder addFilter(Function<Mixer, MultiChannelFilter> filter) {
            filters.add(filter);
            return this;
        }

        public BufferedMixer build() {
            if (bufferTime == null) throw new IllegalArgumentException("bufferTime", new NullPointerException());
            if (sampleRate == null) throw new IllegalArgumentException("sampleRate", new NullPointerException());
            if (channels == null) throw new IllegalArgumentException("channels", new NullPointerException());

            int frames = Math.round(sampleRate * bufferTime);
            int samples = frames * channels;

            BufferedMixer mixer = new BufferedMixer(id, registrant, samples, sampleRate, channels);

            for (MixerSink sink : sinks)
                mixer.addSink(sink);

            for (Function<Mixer, MultiChannelFilter> filters : filters)
                mixer.addFilter(filters.apply(mixer));

            return mixer;
        }
    }
}
