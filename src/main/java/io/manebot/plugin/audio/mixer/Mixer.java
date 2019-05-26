package io.manebot.plugin.audio.mixer;

import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.mixer.filter.MixerFilter;
import io.manebot.plugin.audio.mixer.input.MixerChannel;
import io.manebot.plugin.audio.mixer.output.MixerSink;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
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
    CompletableFuture<MixerChannel> addChannel(MixerChannel channel);
    boolean removeChannel(MixerChannel channel);

    /**
     * Gets an immutable list of filters in the mixer.
     * @return Mixer filters
     */
    Collection<List<MixerFilter>> getFilters();
    List<MixerFilter> addFilter(MixerFilter... channel);
    boolean removeFilter(List<MixerFilter> filterList);

    boolean setRunning(boolean running);

    boolean isFiltering();

    void setFiltering(boolean enable);

    /**
     * Finds if the mixer is running, false othwerise.
     * @return true if running, false otherwise.
     */
    boolean isRunning();

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
     * Empties the mixer.
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
            addFilters(getAudio().getDefaultFilters(getChannels()));
            return this;
        }

        /**
         * Adds a filter to the mixer.
         * @param filter filter function.
         * @return Builder for continuation.
         */
        Builder addFilter(Function<Mixer, Collection<MixerFilter>> filter);

        /**
         * Adds a collection of filters to the mixer.
         * @param filters filter function collection.
         * @return Builder for continuation.
         */
        default Builder addFilters(Function<Mixer, Collection<MixerFilter>>... filters) {
            return addFilters(Arrays.asList(filters));
        }

        /**
         * Adds a collection of filters to the mixer.
         * @param filters filter function collection.
         * @return Builder for continuation.
         */
        default Builder addFilters(Collection<Function<Mixer, Collection<MixerFilter>>> filters) {
            Builder builder = this;
            for (Function<Mixer, Collection<MixerFilter>> filter : filters)
                builder = builder.addFilter(filter);
            return builder;
        }
    }
}
