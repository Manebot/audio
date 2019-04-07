package io.manebot.plugin.audio.api;

import io.manebot.chat.Chat;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.mixer.Mixer;

import java.util.Collection;

public interface AudioConnection {

    /**
     * Gets a collection of mixers registered to this connection.
     * @return Immutable collection of audio mixers.
     */
    Collection<Mixer> getMixers();

    /**
     * Gets audio channels registered to this connection.
     * @return Immutable collection of audio channels.
     */
    Collection<AudioChannel> getChannels();

    /**
     * Gets the audio channel for a specific chat.
     * @param chat Chat instance.
     * @return AudioChannel instance if an audio channel was identified, null otherwise.
     */
    AudioChannel getChannel(Chat chat);

    /**
     * Gets the mixer associated with a specific chat.
     * @param chat Chat instance.
     * @return Mixer instance if a mixer was identified, null otherwise.
     */
    default Mixer getMixer(Chat chat) {
        AudioChannel channel = getChannel(chat);
        if (channel == null) return null;
        else return channel.getMixer();
    }

    /**
     * Finds if the audio connection is connected.
     * @return true if the connection is connected, false otherwise.
     */
    boolean isConnected();

    /**
     * Connects the AudioConnection.
     */
    void connect();

    /**
     * Disconnects the AudioConnection.
     */
    void disconnect();

    void registerChannel(AudioChannel channel);

    boolean unregisterChannel(AudioChannel channel);

    void registerMixer(Mixer mixer);

    boolean unregisterMixer(Mixer mixer);
}
