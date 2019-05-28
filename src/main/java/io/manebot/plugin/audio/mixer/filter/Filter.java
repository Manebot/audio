package io.manebot.plugin.audio.mixer.filter;

/**
 * Represents a mono filter
 */
public interface Filter {

    /**
     * Gets the sample rate of this filter.
     * @return sample rate.
     */
    float getSampleRate();

    /**
     * Resets the filter's state back to the initial filter state.
     */
    default void reset() { }

}
