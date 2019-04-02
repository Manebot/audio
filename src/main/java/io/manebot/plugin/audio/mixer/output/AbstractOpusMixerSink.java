package io.manebot.plugin.audio.mixer.output;

import io.manebot.plugin.audio.opus.OpusEncoder;
import io.manebot.plugin.audio.opus.OpusParameters;
import net.tomp2p.opuswrapper.Opus;

import javax.sound.sampled.AudioFormat;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractOpusMixerSink implements OpusMixerSink {
    public static final AudioFormat AUDIO_FORMAT =
            new AudioFormat(48000, 32, 2, true, false);

    private final Object stateLock = new Object();

    // Audio format
    private final AudioFormat audioFormat; // Audio output format (mostly just ensured to match certain parameters)

    // OPUS variables
    private final OpusParameters opusParameters; // Opus parameters set by configurations
    private volatile OpusEncoder encoder = null; // Opus encoder instance
    private final int opusFrameSize; // Opus frame size (PER CHANNEL!) (also the ASIO chunk size)
    private long opusPacketsEncoded = 0, opusPacketsSent = 0;
    private long opusTime = 0;
    private long networkTime = 0;
    private long opusPosition = 0;
    private long opusBytePosition = 0;

    private long waitTime = 0;

    // Statistics variables
    private long underflowed = 0; // Underflowed samples
    private long overflowed = 0; // Overflowed samples (write returns 0)
    private long clipped = 0; // Clipped samples (outside -1F,1F bounds)
    private long position = 0; // Position in samples

    // I/O variables
    private final float[] sampleBuffer; // ASIO buffer
    private final Queue<OpusPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final int bufferSize; // Buffer size, in samples
    private int availableInput;
    private int samplePosition; // Position, in samples, of the ASIO buffer (available samples)

    // Mixer sink state variables
    private volatile boolean running = false, opening = false;

    protected AbstractOpusMixerSink(AudioFormat audioFormat,
                                    OpusParameters opusParameters,
                                    int bufferSizeInBytes) {
        this.audioFormat = audioFormat;

        if (audioFormat.getSampleRate() != 48000)
            throw new IllegalArgumentException("Invalid audio sample rate: " +
                    audioFormat.getSampleRate() + " != 48000: OPUS requires 48KHz audio");
        else if (audioFormat.getChannels() != 2)
            throw new IllegalArgumentException("Invalid audio channel count: " +
                    audioFormat.getChannels() + " != 2: OPUS requires stereo audio");

        this.bufferSize = bufferSizeInBytes / (audioFormat.getSampleSizeInBits() / 8);
        if (bufferSize <= 0 || bufferSize % audioFormat.getChannels() != 0)
            throw new IllegalArgumentException("invalid bufferSize: " + bufferSize);

        this.sampleBuffer = new float[bufferSize];
        this.availableInput = 0;

        this.opusParameters = opusParameters;
        this.opusFrameSize = (int) getAudioFormat().getSampleRate() / (1000 / opusParameters.getOpusFrameTime());
    }

    @Override
    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    @Override
    public void write(float[] buffer, int len) {
        synchronized (stateLock) {
            if (!running) throw new IllegalStateException("not running");

            if (len > availableInput()) { // This should never happen
                overflowed++; // Probably never will run
                throw new IllegalArgumentException(len + " > " + availableInput());
            }

            if (len > buffer.length) {
                overflowed++;
                throw new IllegalArgumentException(len + " > " + buffer.length);
            }

            if (len <= 0)
                throw new IllegalArgumentException(len + " <= 0");

            if (len % getChannels() != 0)
                throw new IllegalArgumentException("not a full frame");

            // Write to the buffer
            System.arraycopy(buffer, 0, sampleBuffer, samplePosition, len);
            samplePosition += len;
            availableInput -= len;

            // Read samples from the buffer and encode them into packets
            position += encode(false);
        }
    }

    /**
     * Encodes the buffer to packets for the packet queue
     * @return Packets encoded
     */
    private int encode(boolean flush) {
        if (flush) Logger.getGlobal().log(Level.FINE, "Flushing TeamspeakFastMixerSink...");

        int written = 0;
        int frameSize = opusFrameSize * getChannels();
        byte[] encoded;
        long now;
        int copy;

        while (samplePosition >= (flush ? 1 : frameSize)) {
            copy = Math.min(samplePosition, frameSize);
            if (flush && copy < frameSize) {
                for (int i = copy; i < frameSize; i++)
                    sampleBuffer[i] = 0f;

                samplePosition = frameSize;
            }

            if (encoder == null) openOpusEncoder();

            now = System.nanoTime();
            encoded = encoder.encode(sampleBuffer, frameSize);
            opusTime += (System.nanoTime() - now);
            opusPacketsEncoded ++;
            opusPosition += frameSize;

            System.arraycopy(sampleBuffer, frameSize, sampleBuffer, 0, samplePosition - frameSize);
            samplePosition -= frameSize;
            if (packetQueue.add(new OpusPacket(frameSize, encoded)))
                written+=frameSize;
        }

        if (written > 0) opening = false;

        if (flush) Logger.getGlobal().log(Level.FINE, "Flushed TeamspeakFastMixerSink.");

        return written;
    }

    public long getNanotime() {
        return opusTime;
    }
    public long getFrameSize() {
        return opusFrameSize;
    }
    public long getEncoderPosition() {
        return opusPosition;
    }
    public long getNetworkPosition() {
        return opusBytePosition;
    }
    public OpusParameters getEncoderParameters() {
        return opusParameters;
    }

    @Override
    public long getPacketsEncoded() {
        return opusPacketsEncoded;
    }

    @Override
    public long getPacketsSent() {
        return opusPacketsSent;
    }

    /**
     * Finds the count of available samples to be written to write() in the len param
     * @return Available sample count
     */
    @Override
    public int availableInput() {
        return availableInput;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Opens the Opus encoder
     */
    private void openOpusEncoder() {
        synchronized (stateLock) {
            if (encoder != null) return;
            Logger.getGlobal().log(Level.FINE, "Opening TeamspeakFastMixerSink encoder...");

            encoder = new OpusEncoder(
                    (int) getAudioFormat().getSampleRate(), // smp rate (always 48kHz)
                    opusFrameSize,
                    getChannels(),
                    audioFormat.isBigEndian()
            );

            encoder.setEncoderValue(
                    Opus.OPUS_SET_SIGNAL_REQUEST,
                    opusParameters.isOpusMusic() ? Opus.OPUS_SIGNAL_MUSIC : Opus.OPUS_SIGNAL_VOICE
            );

            encoder.setEncoderValue(Opus.OPUS_SET_BITRATE_REQUEST, opusParameters.getOpusBitrate());
            encoder.setEncoderValue(Opus.OPUS_SET_COMPLEXITY_REQUEST, opusParameters.getOpusComplexity());
            encoder.setEncoderValue(Opus.OPUS_SET_VBR_REQUEST, opusParameters.isOpusVbr() ? 1 : 0);
            encoder.setEncoderValue(Opus.OPUS_SET_INBAND_FEC_REQUEST, opusParameters.isOpusFec() ? 1 : 0);

            Logger.getGlobal().log(Level.FINE, "Opened TeamspeakFastMixerSink encoder.");
        }
    }

    private void closeOpusEncoder() {
        synchronized (stateLock) {
            if (encoder != null) {
                Logger.getGlobal().log(Level.FINE, "Closing TeamspeakFastMixerSink encoder...");
                encoder.close();
                encoder = null;
                Logger.getGlobal().log(Level.FINE, "Closed TeamspeakFastMixerSink encoder.");
            }
        }
    }

    @Override
    public void close() {
        if (isRunning()) stop();

        closeOpusEncoder();
    }

    @Override
    public boolean start() {
        synchronized (stateLock) {
            if (running) return false;

            Logger.getGlobal().log(Level.FINE, "Starting TeamspeakFastMixerSink...");

            // Flush buffers, clear outgoing packet queues.
            for (int i = 0; i < sampleBuffer.length; i ++) sampleBuffer[i] = 0f;
            samplePosition = 0;
            availableInput = bufferSize;
            packetQueue.clear();

            // Open (or re-open) Opus encoder.
            openOpusEncoder();

            // Reset the encoder
            if (encoder != null) {
                Logger.getGlobal().log(Level.FINE, "Resetting TeamspeakFastMixerSink...");
                encoder.reset(); // Reset encoder output
                Logger.getGlobal().log(Level.FINE, "Reset TeamspeakFastMixerSink.");
            }

            // Mark as running.
            opening = true;
            running = true;

            Logger.getGlobal().log(Level.FINE, "Started TeamspeakFastMixerSink.");

            return true;
        }
    }

    /**
     * Closes mixer sink
     *
     * Note that we don't close the Opus encoder - just in case there are more samples to write/flush out.
     */
    @Override
    public boolean stop() {
        synchronized (stateLock) {
            if (!running) return false;

            Logger.getGlobal().log(Level.FINE, "Stopping TeamspeakFastMixerSink...");

            try {
                encode(true);
            } catch (RuntimeException e) {
                Logger.getGlobal().log(Level.WARNING, "Problem flushing audio buffer upon close", e);
            }

            running = false;
            Logger.getGlobal().log(Level.FINE, "Stopped TeamspeakFastMixerSink.");

            return true;
        }
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public long getUnderflows() {
        return underflowed;
    }

    @Override
    public long getOverflows() {
        return overflowed;
    }

    /**
     * Called by an encoder thread to determine if the sink is ready to send samples.
     * @return true if the sink has samples to write, or if samples will become available.
     */
    public boolean isReady() {
        return (running && !opening) || packetQueue.size() > 0;
    }

    /**
     * Provides OPUS encoded audio (as a packet) to the caller.
     * @return Encoded OPUS audio packet (zero-length packet if there is an underflow).
     */
    public byte[] provide() {
        long start = System.nanoTime();

        try {
            if (packetQueue.peek() == null) {
                underflowed ++;
                return new byte[0];
            }

            OpusPacket packet = packetQueue.remove();

            if (packet == null) {
                underflowed ++;
                return new byte[0];
            }

            availableInput += packet.getSamples();
            opusBytePosition += packet.getBytes().length;
            opusPacketsSent ++;

            return packet.getBytes();
        } catch (NoSuchElementException ex) {
            underflowed ++;
            return new byte[0];
        } finally {
            long networkTime = System.nanoTime() - start;

            if (networkTime >= (opusParameters.getOpusFrameTime() * 1000000L))
                underflowed++;

            this.networkTime += networkTime;
        }
    }

    public int getChannels() {
        return getAudioFormat().getChannels();
    }

    public long getWaitTime() {
        return waitTime;
    }

    private final class OpusPacket {
        private final int samples;
        private final byte[] bytes;

        OpusPacket(int samples, byte[] bytes) {
            this.samples = samples;
            this.bytes = bytes;
        }

        int getSamples() {
            return samples;
        }

        byte[] getBytes() {
            return bytes;
        }
    }
}
