package io.manebot.plugin.audio.player;

import io.manebot.plugin.audio.mixer.input.MixerChannel;
import io.manebot.user.User;

import javax.sound.sampled.AudioFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public abstract class AudioPlayer implements MixerChannel {
    private final Date started = new Date(System.currentTimeMillis());
    private final User owner;
    private final Type type;
    private final AudioFormat outputFormat;

    protected AudioPlayer(Type type, User owner, AudioFormat outputFormat) {
        this.type = type;
        this.owner = owner;
        this.outputFormat = outputFormat;
    }

    @Override
    public String getName() {
        return owner.getDisplayName() + "'s AudioPlayer";
    }

    public abstract CompletableFuture<AudioPlayer> getFuture();

    public final AudioFormat getOutputFormat() {
        return outputFormat;
    }

    public final User getOwner() {
        return owner;
    }

    public final Type getType() {
        return type;
    }

    /**
     * Gets when the player started.
     * @return Start date.
     */
    public final Date getStarted() {
        return started;
    }

    /**
     * Finds if the player has been closed.
     * @return
     */
    public abstract boolean isClosed();

    /**
     * Finds if the player is playing any audio.
     *
     * Must return true if any samples might be produced by this player.
     *
     * @return true if the player is playing any audio.
     */
    public abstract boolean isPlaying();

    /**
     * Finds if the player is blocking. Blocking players consume a player slot on an audio channel.
     *
     * @return true if the player is blocking, false otherwise.
     */

    public boolean isBlocking() {
        return isPlaying() && type == Type.BLOCKING;
    }

    /**
     * Stops the player softly.  In some implementations this may continuing playing samples (e.g. fade out.)
     *
     * @return true if the player will stop or begin to stop.
     */
    public abstract boolean stop();

    /**
     * Kills the audio player immediately, stopping all playback.
     *
     * @return true if the player was killed, false otherwise.
     */
    public boolean kill() {
        try {
            close();

            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public enum Type {
        /**
         * Played as a solo player (e.g. playlists)
         */
        BLOCKING,

        /**
         * Played as part of a solo player (e.g. voice commands)
         */
        NONBLOCKING
    }
}
