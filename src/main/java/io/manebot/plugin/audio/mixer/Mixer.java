package io.manebot.plugin.audio.mixer;

import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.mixer.filter.Filter;
import io.manebot.plugin.audio.mixer.filter.MultiChannelFilter;
import io.manebot.plugin.audio.mixer.filter.MuxedMultiChannelFilter;
import io.manebot.plugin.audio.mixer.filter.SingleChannelFilter;
import io.manebot.plugin.audio.mixer.input.MixerChannel;
import io.manebot.plugin.audio.mixer.output.MixerSink;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Represents the scaffolding the mixer system in the bot.
 *
 * Channel - audio input (i.e. YouTube video or MP3).  Mixed simultaneously.
 * Sink - audio output target (i.e. Teamspeak)
 * Filter - mixer filters are used to morph data (i.e. clip protection, EQs, stuff)
 */
public interface Mixer {

    String getId();

    /**
     * Gets an immutable list of the sinks in the mixer.
     * @return Mixer sinks
     */
    Collection<MixerSink> getSinks();
    boolean addSink(MixerSink sink);
    boolean removeSink(MixerSink sink);

    /**
     * Gets the registrant of the mixer.
     * @return Mixer registrant.
     */
    MixerRegistrant getRegistrant();

    /**
     * Gets an immutable list of channels in the mixer.
     * @return Mixer channels
     */
    Collection<MixerChannel> getChannels();

    /**
     * Adds a channel to the mixer.
     * @param channel channel to add.
     * @return a CompletableFuture, which is completed when the channel is removed from the mixer (e.g. stopped)
     */
    CompletableFuture<MixerChannel> addChannel(MixerChannel channel);

    /**
     * Removes an existing channel from the mixer.
     * @param channel channel to remove.
     * @return true if the channel was removed, false otherwise.
     */
    boolean removeChannel(MixerChannel channel);

    /**
     * Gets an immutable list of filters in the mixer.
     * @return Mixer filters
     */
    Collection<MultiChannelFilter> getFilters();

    /**
     * Adds a new multi-channel filter to the mixer.
     * @param channels collection of mono filters, mapped to each channel.
     * @return MultiChannelFilter instance.
     */
    MultiChannelFilter addFilter(SingleChannelFilter... channels);

    /**
     * Adds a new multi-channel filter to the mixer.
     * @param filter multi-channel filter to add.
     * @return MultiChannelFilter instance (copy).
     */
    MultiChannelFilter addFilter(MultiChannelFilter filter);

    /**
     * Removes a filter form the mixer.
     * @param filter filter to remove.
     * @return true if the filter was removed, false otherwise.
     */
    boolean removeFilter(MultiChannelFilter filter);

    boolean isFiltering();

    void setFiltering(boolean enable);

    /**
     * Finds if the mixer is running, false othwerise.
     * @return true if running, false otherwise.
     */
    boolean isRunning();

    boolean setRunning(boolean running);

    /**
     * Finds if the mixer should be playing, based on its contained channels.
     * @return true if playing, false otherwise.
     */
    boolean isPlaying();

    /**
     * Finds the number of available samples on the mixer.
     * @return Available samples on the mixer.
     */
    int available();

    /**
     * Empties the mixer, removing all channels immediately.
     */
    void empty();

    /**
     * Processes the mixer buffer.
     * @return true if there are more processes to execute, false otherwise.
     */
    boolean processBuffer();

    /**
     * Gets the buffer size of the mixer, in samples.
     * @return Mixer buffer size, in samples.
     */
    int getBufferSize();

    /**
     * Gets the position of the mixer, in seconds.
     * @return Mixer position in seconds.
     */
    float getPositionInSeconds();

    /**
     * Gets the sample rate of this mixer, which all sinks and channels must conform to.
     * @return Sample rate
     */
    float getAudioSampleRate();

    /**
     * Gets the audio channel count of this mixer, which all sinks and channels must confirm to.
     * @return Channel count.
     */
    int getAudioChannels();

    interface Builder {

        /**
         * Gets the Audio instance for this builder.
         * @return Audio instance.
         */
        Audio getAudio();

        /**
         * Gets the ID of the mixer being created.
         * @return Mixer ID.
         */
        String getId();

        /**
         * Sets the registrant of this mixer.
         * @param registrant registrant to set.
         * @return Builder for continuation.
         */
        Builder setRegistrant(MixerRegistrant registrant);

        /**
         * Sets the buffer time for the mixer.
         * @param seconds seconds.
         * @return Builder for continuation.
         */
        Builder setBufferTime(float seconds);

        /**
         * Sets the native format of this mixer.
         * @param sampleRate sample rate.
         * @param channels channels.
         * @return Builder for continuation.
         */
        Builder setFormat(float sampleRate, int channels);

        /**
         * Gets the sample rate of this mixer.
         * @return sample rate.
         */
        float getSampleRate();

        /**
         * Gets the channel count of this mixer.
         * @return channel count.
         */
        int getChannels();

        /**
         * Adds a sink to this mixer.
         * @param sink mixer sink to add.
         * @return Builder for continuation.
         */
        Builder addSink(MixerSink sink);

        /**
         * Adds default filters
         * @return Builder for continuation.
         */
        default Builder addDefaultFilters() {
            addFilters(getAudio().getDefaultFilters(getSampleRate(), getChannels()));
            return this;
        }

        /**
         * Adds a filter to the mixer.
         * @param filter filter to add.
         * @return Builder for continuation.
         */
        default Builder addFilter(MultiChannelFilter filter) {
            return addFilter((mixer) -> filter);
        }

        /**
         * Adds a filter to the mixer.
         * @param filter filter function.
         * @return Builder for continuation.
         */
        Builder addFilter(Function<Mixer, MultiChannelFilter> filter);

        /**
         * Adds a filter to the mixer.
         * @param filter filter function, providing a single instance of a filter for each channel.
         * @return Builder for continuation.
         */
        default Builder addMuxedMonoFilter(Function<Mixer, SingleChannelFilter> filter) {
            return addFilter((mixer) -> MuxedMultiChannelFilter.from(getChannels(), (ch) -> filter.apply(mixer)));
        }
        /**
         * Adds a filter to the mixer.
         *
         * Note that, if you provide a single instance of a filter that tracks its state by the filtered samples,
         * you may receive unintended audio artifacts, as filters are processed independently by channel. In other
         * words, your single filter instance will be called sequentially, channel after channel, and this could
         * disrupt the state of the filter if your filter tracks its state across calls to filter().
         *
         * @param filter filter, providing the single instance of a filter for each channel.
         * @return Builder for continuation.
         */
        default Builder addMuxedMonoFilter(SingleChannelFilter filter) {
            return addMuxedMonoFilter((mixer) -> filter);
        }

        /**
         * Adds a collection of filters to the mixer.
         * @param filters filter function collection.
         * @return Builder for continuation.
         */
        default Builder addFilters(Iterable<Function<Mixer, MultiChannelFilter>> filters) {
            Builder builder = this;
            for (Function<Mixer, MultiChannelFilter> function : filters)
                builder = builder.addFilter(function);
            return builder;
        }
    }
}
