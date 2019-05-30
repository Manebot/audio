package io.manebot.plugin.audio.player;

import io.manebot.plugin.audio.mixer.input.AudioProvider;
import io.manebot.plugin.audio.resample.Resampler;
import io.manebot.plugin.audio.resample.ResamplerFactory;
import io.manebot.property.Property;
import io.manebot.user.User;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TransitionedAudioPlayer extends AudioPlayer {
    private final Callback callback;

    private long position = 0L;

    private State state = State.INITIALIZE;

    private double durationInSeconds, transitionTimeInSeconds, closeTimeInSeconds = 0D;

    private final Property volumeProperty;

    public TransitionedAudioPlayer(Type type, User owner,
                                   AudioProvider provider,
                                   double durationInSeconds,
                                   double transitionTimeInSeconds,
                                   Callback callback) {
        super(type, owner, provider);

        this.callback = callback;

        this.durationInSeconds = durationInSeconds;
        this.transitionTimeInSeconds = transitionTimeInSeconds;

        this.volumeProperty = getOwner().getEntity().getProperty("Mixer:Volume");
        this.volumeProperty.ensure(1D);
    }

    public boolean isBlocking() {
        return super.isBlocking() && state != State.FADE_OUT;
    }

    @Override
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    @Override
    public boolean isPlaying() {
        return super.isPlaying() && state != State.CLOSED;
    }

    @Override
    public int available() {
        return state != State.CLOSED ? super.available() : 0;
    }


    @Override
    public int read(float[] floats, int offs, int i) throws IOException {
        if (i <= 0) return 0;

        int read;

        try {
            read = super.read(floats, 0, i);
        } catch (EOFException ex) {
            read = -1;
        }

        try {
            if (read <= 0) {
                setState(State.CLOSED);
            } else {
                float volume;
                for (int x = 0; x < read; x += getChannels()) {
                    volume = volumeAtPosition(position + x) * (float) volumeProperty.getDouble();
                    for (int ch = 0; ch < getChannels(); ch++)
                        floats[offs + x + ch] *= volume;
                }

                position += read;
            }
        } catch (Exception ex) {
            throw new IOException(ex);
        }

        return read;
    }

    private boolean setState(State state) throws Exception {
        if (this.state != state) {
            this.state = state;

            if (state == State.FADE_IN) {
                callback.onFadeIn();
            } else if (state == State.FADE_OUT) {
                closeTimeInSeconds = Math.min(getTimeInSeconds(position), durationInSeconds - transitionTimeInSeconds);
                callback.onFadeOut();
            } else if (state == State.CLOSED) {
                try {
                    super.close();
                } finally {
                    callback.onFinished(getTimeInSeconds(position));
                }
            }

            return true;
        }

        return false;
    }

    private double getTimeInSeconds(long position) {
        return (double)(position / getChannels()) / (double)getSampleRate();
    }

    private float volumeAtPosition(long position) throws Exception {
        return volumeAtPosition(getTimeInSeconds(position));
    }

    private float volumeAtPosition(double timeInSeconds) throws Exception {
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
                if (f <= (1f / Math.pow(2D, 32))) {
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
            try {
                setState(State.FADE_OUT);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean kill() {
        try {
            return setState(State.CLOSED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
