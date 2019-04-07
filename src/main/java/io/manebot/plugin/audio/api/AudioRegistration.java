package io.manebot.plugin.audio.api;

import io.manebot.platform.Platform;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.audio.Audio;

public interface AudioRegistration {

    /**
     * Gets the Audio instance associated with this registration.
     * @return Audio instance.
     */
    Audio getAudio();

    /**
     * Gets the plugin associated with this registration.
     * @return Plugin instance.
     */
    default Plugin getPlugin() {
        return getPlatform().getPlugin();
    }

    /**
     * Gets the platform associated with this registration.
     * @return Platform instance.
     */
    Platform getPlatform();

    /**
     * Gets the platform-managed connection associated with this registration.
     * @return AudioConnection instance.
     */
    AudioConnection getConnection();


    interface Builder {

        /**
         * Gets the audio instance.
         * @return audio instance.
         */
        Audio getAudio();

        /**
         * Gets the platform for this audio registration.
         * @return Platform instance.
         */
        Platform getPlatform();

        /**
         * Sets the connection associated with this audio registration.
         * @param connection AudioConnection instance.
         * @return Builder instance for continuance.
         */
        Builder setConnection(AudioConnection connection);

    }
}
