package io.manebot.plugin.audio.mixer;

import io.manebot.plugin.audio.mixer.filter.MixerFilter;
import io.manebot.plugin.audio.mixer.input.AudioProvider;
import io.manebot.plugin.audio.mixer.input.MixerChannel;
import io.manebot.plugin.audio.mixer.output.MixerSink;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class AbstractMixer implements Mixer {
    private final String id;
    private final int bufferSize;

    private final float audioSampleRate;
    private final int audioChannels;

    private final MixerRegistrant registrant;

    private final List<MixerSink> sinks = Collections.synchronizedList(new LinkedList<>());
    private final List<FutureChannel> channels = Collections.synchronizedList(new LinkedList<>());
    private final List<List<MixerFilter>> filters = Collections.synchronizedList(new LinkedList<>());

    private final Object channelLock = new Object();

    public AbstractMixer(String id,
                         MixerRegistrant registrant,
                         int bufferSize, float audioSampleRate, int audioChannels) {
        this.id = id;
        this.registrant = registrant;
        this.bufferSize = bufferSize;

        this.audioSampleRate = audioSampleRate;
        this.audioChannels = audioChannels;
    }

    @Override
    public Collection<MixerSink> getSinks() {
        return Collections.unmodifiableCollection(sinks);
    }

    @Override
    public List<MixerFilter> addFilter(MixerFilter... filterChannels) {
        if (filterChannels.length != getAudioChannels())
            throw new IllegalArgumentException("invalid filter count: channel mismatch");

        List<MixerFilter> filterList = Collections.unmodifiableList(Arrays.asList(filterChannels));

        filters.add(filterList);

        return filterList;
    }

    @Override
    public boolean removeFilter(List<MixerFilter> filterList) {
        return filters.remove(filterList);
    }

    @Override
    public boolean addSink(MixerSink sink) {
        if (sink.getAudioFormat().getSampleRate() != getAudioSampleRate() ||
                sink.getAudioFormat().getChannels() != getAudioChannels())
            throw new IllegalArgumentException("sink format unacceptable");

        if (sinks.add(sink)) {
            if (isRunning()) sink.start();
            else sink.stop();

            return true;
        }

        return false;
    }

    @Override
    public boolean removeSink(MixerSink sink) {
        if (sinks.remove(sink)) {
            sink.stop();
            return true;
        } else return false;
    }

    @Override
    public Collection<MixerChannel> getChannels() {
        return Collections.unmodifiableCollection(
                channels.stream().map(FutureChannel::getChannel).collect(Collectors.toList())
        );
    }

    @Override
    public CompletableFuture<MixerChannel> addChannel(MixerChannel channel) {
        FutureChannel futureChannel = new FutureChannel(channel, new CompletableFuture<>());

        if (channel.getSampleRate() != getAudioSampleRate() || channel.getChannels() != getAudioChannels())
            throw new IllegalArgumentException("format mismatch");

        boolean added;

        synchronized (channelLock) {
            added = channels.add(futureChannel);

            if (!added) throw new IllegalStateException();

            if (!isPlaying()) setRunning(true);

            if (added) {
                //TODO: Events...
            }
        }

        return futureChannel.getFuture();
    }

    @Override
    public boolean removeChannel(MixerChannel channel) {
        boolean removed, stopped;

        synchronized (channelLock) {
            boolean wasPlaying = isPlaying();
            Collection<FutureChannel> futureChannels = channels.stream()
                    .filter((existingChannel) -> existingChannel.getChannel() == channel)
                    .collect(Collectors.toList());

            futureChannels.forEach(futureChannel -> {
                if (channels.remove(futureChannel))
                    futureChannel.getFuture().complete(futureChannel.getChannel());
            });

            removed = futureChannels.size() > 0;
            stopped = removed && wasPlaying && !isPlaying();
        }

        if (stopped) setRunning(false);

        if (removed) {
            // Events...
        }

        return removed;
    }

    @Override
    public Collection<List<MixerFilter>> getFilters() {
        return Collections.unmodifiableCollection(filters);
    }

    @Override
    public boolean isRunning() {
        return getSinks().stream().anyMatch(MixerSink::isRunning);
    }

    @Override
    public boolean isPlaying() {
        return getChannels().size() > 0;
    }

    @Override
    public int available() {
        // Find out how much the sinks can flush down right now
        int sinkAvailable = Math.min(
                getBufferSize(),
                getSinks().stream().filter(MixerSink::isRunning).mapToInt(MixerSink::availableInput).min().orElse(0)
        );

        // Shortcut
        if (sinkAvailable <= 0) return 0;

        // Get the count of samples available in each channel, taking the minimum first
        int channelAvailable = getChannels()
                .stream()
                .filter(MixerChannel::isPlaying)
                .mapToInt(AudioProvider::available)
                .min()
                .orElse(0);

        // Shortcut
        if (channelAvailable <= 0) return 0;

        // Attempt to flush down the minimum amount of samples that all parties agree on
        return Math.min(sinkAvailable, channelAvailable);
    }

    @Override
    public void empty() {
        synchronized (channelLock) {

        }
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public float getAudioSampleRate() {
        return audioSampleRate;
    }

    @Override
    public int getAudioChannels() {
        return audioChannels;
    }

    @Override
    public boolean setRunning(boolean running) {
        if (running) {
            return getSinks().stream().filter(x -> !x.isRunning()).allMatch(MixerSink::start);
        } else {
            boolean stopped = getSinks().stream().filter(MixerSink::isRunning).allMatch(MixerSink::stop);

            // If stopped, reset all filters.
            if (stopped) filters.forEach(filters -> filters.forEach(MixerFilter::reset));

            return stopped;
        }
    }

    @Override
    public MixerRegistrant getRegistrant() {
        return registrant;
    }

    @Override
    public String getId() {
        return id;
    }

    private class FutureChannel {
        private final MixerChannel channel;
        private final CompletableFuture<MixerChannel> future;

        private FutureChannel(MixerChannel channel, CompletableFuture<MixerChannel> future) {
            this.channel = channel;
            this.future = future;
        }

        public MixerChannel getChannel() {
            return channel;
        }

        public CompletableFuture<MixerChannel> getFuture() {
            return future;
        }
    }
}
