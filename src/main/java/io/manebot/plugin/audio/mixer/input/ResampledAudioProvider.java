package io.manebot.plugin.audio.mixer.input;

import io.manebot.plugin.audio.resample.Resampler;

import java.io.EOFException;
import java.io.IOException;

public class ResampledAudioProvider extends BufferedAudioProvider implements AudioProvider {
    /**
     * Provider to resample
     */
    private final AudioProvider provider;

    /**
     * Resampler to use when resampling <b>provider</b>
     */
    private final Resampler resampler;

    private float[] chunk;
    private boolean closed = false, eof = false;

    public ResampledAudioProvider(AudioProvider provider, int bufferSize, Resampler resampler) {
        super(bufferSize);
        this.provider = provider;
        this.resampler = resampler;
    }

    public ResampledAudioProvider(BufferedAudioProvider provider, Resampler resampler) {
        this(provider, resampler.getScaledBufferSize(provider.getBufferSize()), resampler);
    }

    @Override
    public int available() {
        return (!eof && !closed) ? getBufferSize() : super.available();
    }

    @Override
    protected void fillBuffer() throws IOException, EOFException {
        if (closed) throw new IllegalStateException();
        if (eof) throw new EOFException();
        
        int available = Math.min(resampler.getScaledBufferSize(getBuffer().availableInput()), provider.available());

        // Read samples from the player into the buffer
        if (chunk == null || chunk.length < available) chunk = new float[available];
        for (int i = 0; i < available; i ++) chunk[i] = 0f;

        try {
            available = provider.read(chunk, 0, available);
        } catch (EOFException ex) {
            resampler.flush(getBuffer());
            eof = true;
            return;
        }

        // Resample the retrieved samples into the resample buffer
        resampler.resample(chunk, available, getBuffer()::write, getBuffer().availableInput());
    }

    @Override
    public void close() throws Exception {
        if (!closed) {
            resampler.close();
            provider.close();
            closed = true;
        }
    }

    @Override
    public int getSampleRate() {
        return (int) resampler.getOutputFormat().getSampleRate();
    }

    @Override
    public int getChannels() {
        return resampler.getOutputFormat().getChannels();
    }

    @Override
    public String toString() {
        return "Resampled{" + resampler + "," + provider.toString() + "}";
    }
}
