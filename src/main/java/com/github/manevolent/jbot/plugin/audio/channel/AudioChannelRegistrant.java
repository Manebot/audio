package com.github.manevolent.jbot.plugin.audio.channel;

import com.github.manevolent.jbot.plugin.audio.player.AudioPlayer;

public interface AudioChannelRegistrant {
    void onPlayerStarted(AudioChannel channel, AudioPlayer player);
    void onPlayerStopped(AudioChannel channel, AudioPlayer player);

    void onChannelActivated(AudioChannel channel);
    void onChannelPassivated(AudioChannel channel);

    default void onChannelSleep(AudioChannel channel) {
        channel.getMixer().getRegistrant().onChannelSleep(channel.getMixer(), channel);
    }

    default void onChannelWake(AudioChannel channel) {
        channel.getMixer().getRegistrant().onChannelWake(channel.getMixer(), channel);
    }

    default void onChannelUnregistered(AudioChannel channel) {
        channel.onUnregistered();
    }

    default void onChannelRegistered(AudioChannel channel) {
        channel.onRegistered();
    }
}
