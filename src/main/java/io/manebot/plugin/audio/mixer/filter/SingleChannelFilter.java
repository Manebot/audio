package io.manebot.plugin.audio.mixer.filter;

public interface SingleChannelFilter extends Filter {

    /**
     * Calls the filter to process the specified audio buffer.
     *
     * @param buffer Mono PCM samples to process
     * @param offs offset to process at
     * @param len length of samples to process
     * @return processed samples, usually must == len
     */
    int process(float[] buffer, int offs, int len);

}
