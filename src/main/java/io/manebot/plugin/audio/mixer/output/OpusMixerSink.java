package io.manebot.plugin.audio.mixer.output;

import io.manebot.plugin.audio.opus.OpusParameters;

public interface OpusMixerSink extends MixerSink   // TS3J-musicBot Mixer integration interface
{

    /**
     * Gets the Encoder parameters
     * @return Encoder parameters
     */
    OpusParameters getEncoderParameters();

    /**
     * Gets the count of encoded Encoder packets
     * @return Encoder packets
     */
    long getPacketsEncoded();

    /**
     * Gets the count of written encoded Encoder packets
     * @return Encoder packets
     */
    long getPacketsSent();

    /**
     * Gets the time spent encoding Encoder packets, in nanoseconds
     * @return Encoder time
     */
    long getNanotime();

    /**
     * Gets the Encoder frame size for packets
     * @return Encoder frame size
     */
    long getFrameSize();

    /**
     * Gets the Encoder position in samples
     * @return Encoder position
     */
    long getEncoderPosition();

    /**
     * Gets the Encoder network position in bytes
     * @return Encoder network position
     */
    long getNetworkPosition();

    /**
     * Gets the channel count
     * @return Channel count
     */
    int getChannels();

}
