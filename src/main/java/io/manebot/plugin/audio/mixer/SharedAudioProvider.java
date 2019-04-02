package io.manebot.plugin.audio.mixer;

import io.manebot.plugin.audio.mixer.input.AudioProvider;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

public class SharedAudioProvider implements AutoCloseable {
    private final AudioProvider provider;

    private final List<Pipe> pipes = new LinkedList<>();

    private final float[] buffer;

    private int length = 0;

    public SharedAudioProvider(AudioProvider provider, int bufferSize) {
        this.provider = provider;
        this.buffer = new float[bufferSize];
    }

    private int availableToRead() {
        return Math.min(buffer.length, provider.available());
    }

    private boolean readyToAdvance() {
        synchronized (SharedAudioProvider.this) {
            return pipes.size() > 0 && pipes.stream().allMatch(x -> x.position == length);
        }
    }

    public AudioProvider open() {
        synchronized (SharedAudioProvider.this) {
            Pipe pipe = new Pipe();
            pipes.add(pipe);

            Logger.getGlobal().fine("Opened pipe on " + getClass().getName() + ": child of " + provider.toString());
            return pipe;
        }
    }

    @Override
    public void close() throws Exception {
        provider.close();
    }

    public class Pipe implements AudioProvider {
        private int position;
        private int underflowed = 0, overflowed = 0;

        public Pipe() {
            this.position = length;
        }

        @Override
        public int available() {
            synchronized (SharedAudioProvider.this) {
                int available = (length - position);
                if (available <= 0 && readyToAdvance()) return availableToRead();
                else return available;
            }
        }

        @Override
        public int read(float[] buffer, int offs, int len) throws IOException {
            if (len <= 0) return 0;
            else if (len+offs > buffer.length) throw new ArrayIndexOutOfBoundsException(len);

            synchronized (SharedAudioProvider.this) {
                int available = (length - position);

                if (available <= 0 && readyToAdvance()) {
                    // Read a new chunk down
                    length = provider.read(
                            SharedAudioProvider.this.buffer,
                            0,
                            Math.min(SharedAudioProvider.this.buffer.length, provider.available())
                    );

                    // Log if the length is positive
                    if (length > 0)
                        Logger.getGlobal().fine("Advanced " + getClass().getName() + " by " + length + " samples");

                    // We pulled down new data; reset all pipes to 0
                    pipes.forEach(x -> x.position = 0);
                    available = length;
                }

                // Calculate what should be read
                int read = Math.min(available, len);

                // Do work
                if (read > 0) {
                    // Copy to the caller
                    System.arraycopy(SharedAudioProvider.this.buffer, position, buffer, offs, read);

                    position += read;
                    return read;
                } else {
                    // Underflow
                    underflowed++;
                    return 0;
                }
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
        public void close() {
            synchronized (SharedAudioProvider.this) {
                pipes.remove(this);
                Logger.getGlobal().fine("Closed pipe on SharedAudioProvider for " + provider.toString());
            }
        }

        public int getUnderflowed() {
            return underflowed;
        }

        public int getOverflowed() {
            return overflowed;
        }
    }
}
