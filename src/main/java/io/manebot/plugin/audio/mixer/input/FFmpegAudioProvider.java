package io.manebot.plugin.audio.mixer.input;

import com.github.manevolent.ffmpeg4j.AudioFrame;
import com.github.manevolent.ffmpeg4j.FFmpegException;
import com.github.manevolent.ffmpeg4j.FFmpegInput;
import com.github.manevolent.ffmpeg4j.MediaType;
import com.github.manevolent.ffmpeg4j.source.AudioSourceSubstream;
import com.github.manevolent.ffmpeg4j.source.MediaSourceSubstream;
import com.github.manevolent.ffmpeg4j.stream.source.FFmpegSourceStream;

import org.bytedeco.javacpp.avformat;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

public class FFmpegAudioProvider extends BufferedAudioProvider implements Runnable {
    private final AudioSourceSubstream substream;
    private final Object nativeLock = new Object();
    private volatile boolean eof, closed = false;

    private IOException unhandled;
    private Thread fillThread;
    private final Object fillLock = new Object();

    public FFmpegAudioProvider(AudioSourceSubstream substream, int bufferSize) {
        super(bufferSize);

        this.substream = substream;
    }

    @Override
    public int available() {
        return (!eof && !closed) ? getBufferSize() : super.available();
    }

    /**
     * Completely or partially fills the buffer, providing as many samples as possible to the internal buffer in a
     * single pass.
     *
     * @throws IOException if there is an unexpected, unrecoverable error when
     *                      reading from the stream or writing into the buffer.
     * @throws EOFException if the end of the stream is reached.
     */
    @Override
    protected void fillBuffer() throws IOException, EOFException {
        if (closed) throw new IllegalStateException();

        synchronized (fillLock) {
            if (unhandled != null) throw unhandled;
            if (eof) throw new EOFException();

            // Attempt to revive the pipe thread
            if (fillThread == null || !fillThread.isAlive())
                (fillThread = new Thread(this)).start();

            while (getBuffer().availableOutput() <= 0 && fillThread != null && fillThread.isAlive()) {
                fillLock.notifyAll();

                try {
                    fillLock.wait();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }

                if (unhandled != null) throw unhandled;
            }
        }
    }

    @Override
    public int read(float[] buffer, int offs, int len) throws IOException, EOFException {
        int read = super.read(buffer, offs, len);

        if (read > 0 && getBuffer().availableInput() > 0 && fillThread != null && fillThread.isAlive()) {
            synchronized (fillLock) {
                fillLock.notifyAll();
            }
        }

        return read;
    }

    @Override
    public void run() {
        AudioFrame frame = null;
        int framePosition = 0;

        try {
            while (!closed) {
                while (!closed && getBuffer().availableInput() > 0) {
                    if (frame == null || framePosition >= frame.getLength()) {
                        try {
                            synchronized (nativeLock) {
                                if (closed) throw new IllegalStateException();
                                frame = substream.next();
                            }

                            framePosition = 0;
                        } catch (EOFException eof) {
                            return;
                        }

                        if (frame == null) continue; // try again
                    }

                    int len = Math.min(getBuffer().availableInput(), frame.getLength() - framePosition);

                    int written = getBuffer().write(
                            frame.getSamples(),
                            framePosition,
                            len
                    );

                    framePosition += written;

                    if (written > 0) {
                        synchronized (fillLock) {
                            fillLock.notifyAll();
                        }
                    }
                }

                synchronized (fillLock) {
                    fillLock.notifyAll();
                    fillLock.wait();
                }
            }
        } catch (InterruptedException ex) {
            // ignore
        } catch (IOException ex) {
            this.unhandled = ex;
        } catch (Throwable ex) {
            this.unhandled = new IOException("Unexpected problem in audio pipe thread", ex);
        } finally {
            synchronized (fillLock) {
                fillThread = null;
                eof = true;
                fillLock.notifyAll();
            }
        }
    }

    @Override
    public int getSampleRate() {
        return substream.getFormat().getSampleRate();
    }

    @Override
    public int getChannels() {
        return substream.getFormat().getChannels();
    }

    @Override
    public void close() throws Exception {
        boolean acted = false;

        synchronized (nativeLock) {
            if (!closed) {
                substream.getParent().close();
                closed = true;
                acted = true;
            }
        }

        if (acted && fillThread != null && fillThread.isAlive()) {
            fillThread.interrupt();
            fillThread = null;
        }
    }

    public static FFmpegAudioProvider open(
            avformat.AVInputFormat inputFormat,
            InputStream inputStream,
            int bufferSize
    ) throws FFmpegException {
        FFmpegInput input = new FFmpegInput(inputStream);
        return open(input, inputFormat, bufferSize);
    }

    public static FFmpegAudioProvider open(
            FFmpegInput input,
            avformat.AVInputFormat format,
            int bufferSize
    ) throws FFmpegException {
        return open(input.open(format), bufferSize);
    }

    public static FFmpegAudioProvider open(
            FFmpegSourceStream stream,
            int bufferSize
    ) throws FFmpegException {
        // disable all substreams until we find one we want
        for (MediaSourceSubstream substream : stream.registerStreams())
            substream.setDecoding(false);

        // find the first substream and choose to decode that
        for (MediaSourceSubstream substream : stream.registerStreams()) {
            if (substream.getMediaType() != MediaType.AUDIO) {
                continue;
            }

            AudioSourceSubstream audioSourceSubstream = (AudioSourceSubstream) substream;
            substream.setDecoding(true);
            return new FFmpegAudioProvider(audioSourceSubstream, bufferSize);
        }

        throw new FFmpegException("no audio substreams found in input");
    }

    public static FFmpegAudioProvider open(
            FFmpegSourceStream stream,
            double bufferSeconds
    ) throws FFmpegException {
        // disable all substreams until we find one we want
        for (MediaSourceSubstream substream : stream.registerStreams())
            substream.setDecoding(false);

        // find the first substream and choose to decode that
        for (MediaSourceSubstream substream : stream.registerStreams()) {
            if (substream.getMediaType() != MediaType.AUDIO) {
                continue;
            }

            AudioSourceSubstream audioSourceSubstream = (AudioSourceSubstream) substream;
            substream.setDecoding(true);

            int samples = (int) Math.ceil(
                    (audioSourceSubstream.getFormat().getSampleRate() * audioSourceSubstream.getFormat().getChannels())
                    * bufferSeconds
            );

            return new FFmpegAudioProvider(audioSourceSubstream, samples);
        }

        throw new FFmpegException("no audio substreams found in input");
    }
}
