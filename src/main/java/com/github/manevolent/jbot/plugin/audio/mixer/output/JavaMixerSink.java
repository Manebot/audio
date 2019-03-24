package com.github.manevolent.jbot.plugin.audio.mixer.output;

import com.github.manevolent.jbot.plugin.audio.resample.SampleConvert;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * PCM-based mixer sink.  Sinks to a SourceDataLine.
 */
public class JavaMixerSink implements MixerSink {
    private final SourceDataLine dataLine;
    private final byte[] nativeBuffer;
    private final int bufferSize;
    private final int sampleLen;

    private long position = 0L;
    private long clipped = 0L;
    private long underflows, overflows;

    private final SinkWriter writer;

    public JavaMixerSink(SourceDataLine dataLine) {
        this(dataLine, dataLine.getBufferSize()/(dataLine.getFormat().getSampleSizeInBits()/8));
    }

    public JavaMixerSink(SourceDataLine dataLine, int bufferSize) {
        if (dataLine.getFormat().getEncoding() != AudioFormat.Encoding.PCM_SIGNED)
            throw new IllegalArgumentException("sample encoding must be PCM_SIGNED");

        if (dataLine.getFormat().isBigEndian())
            throw new IllegalArgumentException("sample format must be little-endian");

        this.dataLine = dataLine;
        this.sampleLen = (dataLine.getFormat().getSampleSizeInBits() / 8);

        // Calculate buffer and its size
        this.bufferSize = bufferSize*sampleLen;
        this.nativeBuffer = new byte[this.bufferSize];

        switch (dataLine.getFormat().getSampleSizeInBits()) {
            case 8:
                writer = (b, offs, smp) -> b[offs] = (byte) (Byte.MIN_VALUE + (smp * (Byte.MAX_VALUE - Byte.MIN_VALUE)));
                break;
            case 16:
                writer = (b, offs, smp) -> {
                    SampleConvert.intToBytes16_optimized_LE(
                            SampleConvert.floatToShort(smp),
                            nativeBuffer,
                            offs
                    );
                };

                break;
            case 24:
                writer = (b, offs, smp) -> {
                    SampleConvert.intToBytes24_optimized_LE(
                            SampleConvert.floatToInt24(smp),
                            nativeBuffer,
                            offs
                    );
                };

                break;
            case 32:
                writer = (b, offs, smp) -> {
                    SampleConvert.intToBytes32_optimized_LE(
                            SampleConvert.floatToInt32(smp),
                            nativeBuffer,
                            offs
                    );
                };

                break;
            default:
                throw new IllegalArgumentException("Unsupported bit depth: " + dataLine.getFormat().getSampleSizeInBits());
        }
    }

    @Override
    public AudioFormat getAudioFormat() {
        return dataLine.getFormat();
    }

    @Override
    public void write(float[] buffer, int len) {
        if (len <= 0)
            throw new IllegalArgumentException("invalid operation: attempted to write <= 0 samples.");

        if (buffer.length < len)
            throw new IllegalArgumentException("buffer underflow: attempted to write " +
                    len + " samples from a buffer only " +
                    buffer.length + " samples long.");

        if (len % dataLine.getFormat().getChannels() != 0)
            throw new IllegalArgumentException("invalid operation: " +
                    "buffer does not align with frame requirement (" +
                    dataLine.getFormat().getChannels() + "samples/frame)");

        int samplesWritten = 0;

        for (int i = 0; i < len; i ++) {
            writer.write(nativeBuffer, i * sampleLen, buffer[i]);
        }

        int availableSamples = availableInput();
        if (availableSamples >= getBufferSize()) underflows++;
        else if (availableSamples <= 0) overflows++;

        int offs;
        int outputLength = (len * sampleLen);
        while (samplesWritten < len) {
            offs = samplesWritten * sampleLen;
            samplesWritten += dataLine.write(nativeBuffer, offs, outputLength - offs) / sampleLen;
        }

        position += samplesWritten;

        // Actually starts the mixer
        if (!dataLine.isRunning()) dataLine.start();
    }

    @Override
    public int availableInput() {
        // NOTE: This could pose a problem if somehow the dataLine's available() could ever be
        // greater than bufferSize. I want to keep bufferSize as the ceiling because I want to
        // know if a bufferSize is too small -- it will cause stuttering instead of expending
        // extra CPU cycles to keep up with the audio.
        return Math.min(getBufferSize(), dataLine.available() / sampleLen);
    }

    @Override
    public boolean isRunning() {
        return dataLine.isOpen();
    }

    @Override
    public boolean stop() {
        dataLine.flush();
        dataLine.stop();

        return true;
    }

    @Override
    public boolean start() {
        if (!dataLine.isOpen())
            try {
                dataLine.open(dataLine.getFormat(), bufferSize);
            } catch (LineUnavailableException e) {
                throw new RuntimeException(e);
            }

        dataLine.start();

        return true;
    }

    @Override
    public int getBufferSize() {
        return bufferSize / sampleLen;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public long getUnderflows() {
        return underflows;
    }

    @Override
    public long getOverflows() {
        return overflows;
    }

    private interface SinkWriter {
        void write(byte[] b, int offs, float smp);
    }

}
