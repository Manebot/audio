package io.manebot.plugin.audio.player;

import io.manebot.plugin.audio.mixer.input.AudioProvider;
import io.manebot.plugin.audio.mixer.input.MixerChannel;
import io.manebot.plugin.audio.resample.Resampler;
import io.manebot.plugin.audio.resample.ResamplerFactory;
import io.manebot.user.User;

import javax.sound.sampled.AudioFormat;
import java.io.EOFException;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

public class AudioPlayer implements MixerChannel {
    private final Date started = new Date(System.currentTimeMillis());
    private final User owner;
    private final Type type;
    private final AudioProvider provider;
    private final CompletableFuture<AudioPlayer> future = new CompletableFuture<>();

    private boolean closed = false, eof = false;

    public AudioPlayer(Type type, User owner, AudioProvider provider) {
        this.type = type;
        this.owner = owner;
        this.provider = provider;
    }

    @Override
    public String getName() {
        return owner.getDisplayName() + "'s AudioPlayer";
    }

    /**
     * Gets the user who created this audio player.
     * @return owning user.
     */
    public final User getOwner() {
        return owner;
    }

    /**
     * Gets the type of the audio player.
     * @return type.
     */
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
     * Gets the future associated with this audio player.
     * This future is completed when the audio player is <i>fully closed</i>.
     * @return future instance.
     */
    public CompletableFuture<AudioPlayer> getFuture() {
        return future;
    }

    /**
     * Finds if the player has been closed.
     * @return
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Finds if the player is playing any audio.
     *
     * Must return true if any samples might be produced by this player.
     *
     * @return true if the player is playing any audio.
     */
    public boolean isPlaying() {
        return !eof;
    }

    /**
     * Stops the player softly.  In some implementations this may continuing playing samples (e.g. fade out.)
     *
     * @return true if the player will stop or begin to stop.
     */
    public boolean stop() {
        return kill();
    }

    /**
     * Finds if the player is blocking. Blocking players consume a player slot on an audio channel.
     *
     * @return true if the player is blocking, false otherwise.
     */

    public boolean isBlocking() {
        return isPlaying() && type == Type.BLOCKING;
    }

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

    @Override
    public int available() {
        return provider.available();
    }

    @Override
    public int read(float[] buffer, int offs, int len) throws IOException, EOFException {
        if (closed) throw new IllegalStateException();
        if (eof) throw new EOFException();

        try {
            return provider.read(buffer, offs, len);
        } catch (EOFException eof) {
            this.eof = true;
            return 0;
        }
    }

    @Override
    public int getSampleRate() {
        return provider.getSampleRate();
    }

    @Override
    public int getChannels() {
        return provider.getChannels();
    }

    @Override
    public void close() throws Exception {
        if (!closed) {
            provider.close();
            eof = true;
            closed = true;

            future.complete(this);
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
