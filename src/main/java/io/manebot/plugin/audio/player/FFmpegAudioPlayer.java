package io.manebot.plugin.audio.player;

import com.github.manevolent.ffmpeg4j.*;
import com.github.manevolent.ffmpeg4j.source.AudioSourceSubstream;
import com.github.manevolent.ffmpeg4j.source.MediaSourceSubstream;
import com.github.manevolent.ffmpeg4j.stream.source.FFmpegSourceStream;
import io.manebot.user.User;
import org.bytedeco.javacpp.avformat;

import javax.sound.sampled.AudioFormat;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class FFmpegAudioPlayer extends BufferedAudioPlayer {
    private final AudioSourceSubstream substream;
    private final Object nativeLock = new Object();
    private volatile boolean eof, closed = false;
    private final AudioFormat format;
    private final CompletableFuture<AudioPlayer> future = new CompletableFuture<>();

    public FFmpegAudioPlayer(Type type,
                             User owner,
                             AudioFormat outputFormat,
                             int bufferSize,
                             AudioSourceSubstream substream) {
        super(type, owner, outputFormat, bufferSize);

        this.substream = substream;

        this.format = new AudioFormat(
                substream.getFormat().getSampleRate(),
                32,
                substream.getFormat().getChannels(),
                true,
                false
        );
    }

    @Override
    public int read(float[] buffer, int offs, int len) throws IOException {
        int read = 0;

        while (read < len) {
            while (getBuffer().availableOutput() <= 0)
                if (!processBuffer()) return read;

            read += getBuffer().read(buffer, offs + read, Math.min(len - read, getBuffer().availableOutput()));
        }

        return read;
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
    protected boolean processBuffer() throws IOException {
        synchronized (nativeLock) {
            if (eof) return false;

            AudioFrame frame;

            try {
                frame = substream.next();
            } catch (EOFException ex) {
                // EOF
                eof = true;
                return false;
            }

            if (frame == null)
                return true; // try again

            if (frame.getSamples().length <= 0)
                return true; // try again

            getBuffer().write(frame.getSamples(), frame.getLength());

            return true;
        }
    }


    public boolean hasReachedEof() {
        return eof;
    }

    @Override
    public CompletableFuture getFuture() {
        return future;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isPlaying() {
        return !isClosed() && (!hasReachedEof() || getBuffer().availableOutput() > 0);
    }

    @Override
    public boolean stop() {
        return kill();
    }

    @Override
    public void close() throws Exception {
        boolean acted = false;

        synchronized (nativeLock) {
            if (!closed) {
                substream.getParent().close();
                eof = true;
                closed = true;
                acted = true;
            }
        }

        future.complete(this);
    }

    public final AudioFormat getFormat() {
        return format;
    }

    public static FFmpegAudioPlayer open(
            Type type,
            User owner,
            avformat.AVInputFormat inputFormat,
            InputStream inputStream,
            int bufferSize
    ) throws FFmpegException {
        FFmpegInput input = new FFmpegInput(inputStream);
        return open(type, owner, input, inputFormat, bufferSize);
    }

    public static FFmpegAudioPlayer open(
            Type type,
            User owner,
            FFmpegInput input,
            avformat.AVInputFormat format,
            int bufferSize
    ) throws FFmpegException {
        return open(type, owner, input.open(format), bufferSize);
    }

    public static FFmpegAudioPlayer open(
            Type type,
            User owner,
            FFmpegSourceStream stream,
            int bufferSize
    ) throws FFmpegException {
        for (MediaSourceSubstream substream : stream.registerStreams())
            substream.setDecoding(false);

        for (MediaSourceSubstream substream : stream.registerStreams()) {
            if (substream.getMediaType() != MediaType.AUDIO) {
                continue;
            }

            AudioSourceSubstream audioSourceSubstream = (AudioSourceSubstream) substream;

            substream.setDecoding(true);

            return new FFmpegAudioPlayer(
                    type,
                    owner,
                    new AudioFormat(
                            ((AudioSourceSubstream) substream).getFormat().getSampleRate(),
                            32,
                            ((AudioSourceSubstream) substream).getFormat().getChannels(),
                            true,
                            false
                    ),
                    bufferSize,
                    audioSourceSubstream
            );
        }

        throw new FFmpegException("no audio substreams found in input");
    }
}
