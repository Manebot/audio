package io.manebot.plugin.audio.mixer;

import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.mixer.input.MixerChannel;

public interface MixerRegistrant {
    default void onMixerRegistered(Mixer mixer) {

    }

    default void onMixerUnregistered(Mixer mixer) {

    }

    default void onMixerStarted(Mixer mixer) {}
    default void onMixerStopped(Mixer mixer) {}

    default void onChannelAdded(Mixer mixer, MixerChannel channel) {}
    default void onChannelRemoved(Mixer mixer, MixerChannel channel) {}

    default void onChannelSleep(Mixer mixer, AudioChannel channel) {}
    default void onChannelWake(Mixer mixer, AudioChannel channel) {}
}
