package io.manebot.plugin.audio.player;

import io.manebot.property.Property;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TransitionedAudioPlayer extends AudioPlayer {
    private final AudioPlayer player;
    private final Callback callback;

    private long position = 0L;

    private State state = State.INITIALIZE;

    private double durationInSeconds,
            transitionTimeInSeconds,
            closeTimeInSeconds = 0D;

    private final Property volumeProperty;

    public TransitionedAudioPlayer(AudioPlayer player,
                                   double durationInSeconds,
                                   double transitionTimeInSeconds,
                                   Callback callback) {
        super(player.getType(), player.getOwner(), player.getOutputFormat());

        this.callback = callback;
        this.player = player;

        this.durationInSeconds = durationInSeconds;
        this.transitionTimeInSeconds = transitionTimeInSeconds;

        this.volumeProperty = player.getOwner().getEntity().getPropery("Mixer:Volume");
        this.volumeProperty.ensure(1D);
    }

    public boolean isBlocking() {
        return super.isBlocking() &&
                player.isBlocking() &&
                state != State.FADE_OUT;
    }

    @Override
    public Future getFuture() {
        return player.getFuture();
    }

    @Override
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    @Override
    public boolean isPlaying() {
        return player.isPlaying() && state != State.CLOSED;
    }

    @Override
    public int available() {
        return state != State.CLOSED ? player.available() : 0;
    }

    @Override
    public int getSampleRate() {
        return player.getSampleRate();
    }

    @Override
    public int getChannels() {
        return player.getChannels();
    }

    @Override
    public int read(float[] floats, int offs, int i) throws IOException {
        if (i <= 0) return 0;

        int read;

        try {
            read = player.read(floats, 0, i);
        } catch (EOFException ex) {
            read = -1;
        }

        if (read <= 0) {
            setState(State.CLOSED);
        } else {
            float volume;
            for (int x = 0; x < read; x += getOutputFormat().getChannels()) {
                volume = volumeAtPosition(position + x) * (float) volumeProperty.getDouble();
                for (int ch = 0; ch < getOutputFormat().getChannels(); ch ++)
                    floats[offs + x + ch] *= volume;
            }

            position += read;
        }

        return read;
    }

    private boolean setState(State state) {
        if (this.state != state) {
            Logger.getGlobal().fine("Changing " + getClass().getName() + " state: " + this.state + " -> " + state);

            this.state = state;

            if (state == State.FADE_IN) {
                callback.onFadeIn();
            } else if (state == State.FADE_OUT) {
                closeTimeInSeconds = Math.min(getTimeInSeconds(position), durationInSeconds - transitionTimeInSeconds);
                callback.onFadeOut();
            } else if (state == State.CLOSED) {
                try {
                    player.close();
                } catch (Exception e) {
                    Logger.getGlobal().log(Level.SEVERE, "Problem closing transitioned audio player", e);
                }

                // Call the callback.
                callback.onFinished(getTimeInSeconds(position));
            }

            return true;
        }

        return false;
    }

    private double getTimeInSeconds(long position) {
        return (double)(position/player.getOutputFormat().getChannels())
                / (double)player.getOutputFormat().getSampleRate();
    }

    private float volumeAtPosition(long position) {
        return volumeAtPosition(getTimeInSeconds(position));
    }

    private float volumeAtPosition(double timeInSeconds) {
        float f;
        boolean shouldFadeOut = durationInSeconds - timeInSeconds <= transitionTimeInSeconds;

        switch (state) {
            case INITIALIZE:
                setState(State.FADE_IN);
            case FADE_IN:
                // Calculate fade in
                f = (float)(Math.pow(timeInSeconds, 0.5d) / Math.pow(transitionTimeInSeconds, 0.5d));

                if (shouldFadeOut || f >= 1f)
                    setState(State.NORMAL); // To normal
                else
                    return Math.max(0f, Math.min(1f, f)); // Still fading in
            case NORMAL:
                if (shouldFadeOut)
                    setState(State.FADE_OUT); // Now fading out (drop to next statement)
                else
                    return 1f; // Still normal
            case FADE_OUT:
                f = 1f - ((float)(Math.pow(timeInSeconds - closeTimeInSeconds, 0.5d) /
                        Math.pow(transitionTimeInSeconds, 0.5d)));

                // Handle cancellation by finding if the audio is too quiet to be heard.
                if (f <= (1f / Math.pow(2D, getOutputFormat().getSampleSizeInBits()))) {
                    setState(State.CLOSED);
                } else {
                    f= Math.max(0f, Math.min(1f, Math.min(
                            f,
                            (float) (Math.pow(timeInSeconds, 0.5d) / (float) Math.pow(transitionTimeInSeconds, 0.5d))
                    ))); // Still fading out

                    return f;
                }
            case CLOSED: // (kek)
                return 0f;
            default:
                return 0f;
        }
    }

    @Override
    public boolean stop() {
        State now = state;
        if (now != State.FADE_OUT && now != State.CLOSED) {
            setState(State.FADE_OUT);
            return true;
        }

        return false;
    }

    @Override
    public boolean kill() {
        return setState(State.CLOSED);
    }

    @Override
    public void close() throws Exception {
        setState(State.CLOSED);
    }

    public interface Callback {
        void onFadeIn();
        void onFadeOut();
        void onFinished(double timePlayedInSeconds);
    }

    public enum State {
        INITIALIZE,
        FADE_IN,
        NORMAL,
        FADE_OUT,
        CLOSED
    }
}
