package com.github.manevolent.jbot.plugin.audio.mixer.input;

public interface MixerChannel extends AudioProvider {

    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Finds if the channel is still playing audio.
     * @return true if the channel is playing audio.
     */
    boolean isPlaying();

    interface Registrant {
        void onChannelAdded(MixerChannel channel);
        void onChannelRemoved(MixerChannel channel);
    }
}
