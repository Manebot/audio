package io.manebot.plugin.audio.channel;

import io.manebot.plugin.audio.mixer.MixerRegistrant;
import io.manebot.plugin.audio.player.AudioPlayer;

public interface AudioChannelRegistrant {
    void onPlayerStarted(AudioChannel channel, AudioPlayer player);
    void onPlayerStopped(AudioChannel channel, AudioPlayer player);

    void onChannelActivated(AudioChannel channel);
    void onChannelPassivated(AudioChannel channel);

    default void onChannelSleep(AudioChannel channel) {
        MixerRegistrant registrant = channel.getMixer().getRegistrant();
        if (registrant != null) {
            registrant.onChannelSleep(channel.getMixer(), channel);
        }
    }

    default void onChannelWake(AudioChannel channel) {
        MixerRegistrant registrant = channel.getMixer().getRegistrant();
        if (registrant != null) {
            registrant.onChannelWake(channel.getMixer(), channel);
        }
    }

    default void onChannelUnregistered(AudioChannel channel) {
        channel.onUnregistered();
    }

    default void onChannelRegistered(AudioChannel channel) {
        channel.onRegistered();
    }
}
