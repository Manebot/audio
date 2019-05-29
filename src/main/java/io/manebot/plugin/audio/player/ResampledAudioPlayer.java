package io.manebot.plugin.audio.player;

import io.manebot.plugin.audio.AudioBuffer;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.audio.resample.Resampler;
import io.manebot.plugin.audio.resample.ResamplerFactory;
import io.manebot.user.User;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ResampledAudioPlayer extends BufferedAudioPlayer {
    private final AudioPlayer player;
    private final Resampler resampler;
    private final AudioBuffer resampleBuffer;
    private final CompletableFuture<AudioPlayer> future = new CompletableFuture<>();

    private boolean closed = false;

    public ResampledAudioPlayer(AudioPlayer player,
                                AudioFormat outputFormat,
                                int bufferSize,
                                Resampler resampler) {
        super(player.getType(), player.getOwner(), outputFormat, bufferSize);

        this.player = player;
        this.resampler = resampler;
        this.resampleBuffer = new AudioBuffer(resampler.getScaledBufferSize(bufferSize));
    }

    @Override
    public CompletableFuture<AudioPlayer> getFuture() {
        return future;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isPlaying() {
        return player.isPlaying();
    }

    @Override
    public boolean isBlocking() {
        return player.isBlocking();
    }

    @Override
    public boolean stop() {
        boolean stopped = player.stop();

        if (stopped) resampler.flush(getBuffer());

        return stopped;
    }

    @Override
    protected boolean processBuffer() throws IOException {
        int available = Math.min(resampleBuffer.availableInput(), player.available());

        // Read samples from the player into the buffer
        int read = resampleBuffer.write(player, available);

        // EOF check
        if (read <= 0) return false;

        // Resample the retrieved samples into the resample buffer
        int resampled = resampler.resample(resampleBuffer, getBuffer());

        return isPlaying();
    }

    @Override
    public void close() throws Exception {
        if (!player.isClosed())
            player.close();

        if (!closed) {
            resampler.close();
            closed = true;
            future.complete(this);
        }
    }

    public AudioFormat getFormat() {
        return resampler.getOutputFormat();
    }

    @Override
    public int getSampleRate() {
        return (int) resampler.getOutputFormat().getSampleRate();
    }

    @Override
    public int getChannels() {
        return resampler.getOutputFormat().getChannels();
    }

    public static ResampledAudioPlayer wrap(AudioPlayer player,
                                            int bufferSize, AudioFormat target,
                                            ResamplerFactory resamplerFactory) {
        return new ResampledAudioPlayer(
                player,
                target,
                bufferSize,
                resamplerFactory.create(player.getOutputFormat(), target, bufferSize)
        );
    }

    public static ResampledAudioPlayer wrap(AudioPlayer player,
                                            Mixer mixer,
                                            ResamplerFactory resamplerFactory) {
        AudioFormat audioFormat = new AudioFormat(
                mixer.getAudioSampleRate(),
                32,
                mixer.getAudioChannels(),
                true,
                false
        );

        return wrap(player, mixer.getBufferSize(), audioFormat, resamplerFactory);
    }
}
