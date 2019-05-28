package io.manebot.plugin.audio.mixer.filter;

public interface MultiChannelFilter extends Filter {

    /**
     * Gets the channel count of this filter.
     * @return channel count.
     */
    int getChannels();

    /**
     * Calls the filter to process the specified audio buffer.
     *
     * @param buffer samples to process
     * @param offs offset to process at in each channel buffer
     * @param len length of samples to process in each channel buffer
     * @return processed samples, usually must == len
     */
    int process(float[][] buffer, int offs, int len);

}
