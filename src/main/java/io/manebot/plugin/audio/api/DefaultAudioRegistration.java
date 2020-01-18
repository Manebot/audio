package io.manebot.plugin.audio.api;

import io.manebot.platform.Platform;
import io.manebot.plugin.audio.Audio;

import java.util.Objects;

public final class DefaultAudioRegistration implements AudioRegistration {
    private final Audio audio;
    private final Platform platform;
    private final AudioConnection connection;

    private DefaultAudioRegistration(Audio audio, Platform platform, AudioConnection connection) {
        this.audio = audio;
        this.platform = platform;
        this.connection = connection;
    }

    @Override
    public Audio getAudio() {
        return audio;
    }

    @Override
    public Platform getPlatform() {
        return platform;
    }

    @Override
    public AudioConnection getConnection() {
        return connection;
    }

    public static class Builder implements AudioRegistration.Builder {
        private final Audio audio;
        private final Platform platform;

        private AudioConnection connection;

        public Builder(Audio audio, Platform platform) {
            this.audio = audio;
            this.platform = platform;
        }

        @Override
        public Audio getAudio() {
            return audio;
        }

        @Override
        public Platform getPlatform() {
            return platform;
        }

        @Override
        public AudioRegistration.Builder setConnection(AudioConnection connection) {
            this.connection = connection;
            return this;
        }

        public DefaultAudioRegistration build() {
            return new DefaultAudioRegistration(audio, platform, Objects.requireNonNull(connection, "connection not supplied"));
        }
    }
}
