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

public class FFmpegAudioProvider extends BufferedAudioProvider {
    private final AudioSourceSubstream substream;
    
    private AudioFrame frame;
    private int framePosition = 0;
    private boolean eof, closed = false;

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
    
        while (getBuffer().availableInput() > 0) {
            if (frame == null || framePosition >= frame.getLength()) {
                frame = substream.next();
                framePosition = 0;
            }
    
            framePosition += getBuffer().write(frame.getSamples(), framePosition, Math.min(getBuffer().availableInput(), frame.getLength() - framePosition));
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
        if (!closed) {
            substream.getParent().close();
            closed = true;
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
