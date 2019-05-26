package io.manebot.plugin.audio.opus;

import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;

public class OpusParameters {
    private final int opusFrameTime;
    private final int opusBitrate;
    private final int opusComplexity;
    private final int opusPacketLossPercent;
    private final boolean opusVbr;
    private final boolean opusFec;
    private final boolean opusMusic;

    public OpusParameters(int opusFrameTime, int opusBitrate,
                          int opusComplexity, int opusPacketLossPercent,
                          boolean opusVbr, boolean opusFec, boolean opusMusic) {
        this.opusFrameTime = opusFrameTime;
        this.opusBitrate = opusBitrate;
        this.opusComplexity = opusComplexity;
        this.opusPacketLossPercent = opusPacketLossPercent;
        this.opusVbr = opusVbr;
        this.opusFec = opusFec;
        this.opusMusic = opusMusic;
    }

    public int getOpusFrameTime() {
        return opusFrameTime;
    }

    public int getOpusBitrate() {
        return opusBitrate;
    }

    public int getOpusComplexity() {
        return opusComplexity;
    }

    public int getOpusPacketLossPercent() {
        return opusPacketLossPercent;
    }

    public boolean isOpusVbr() {
        return opusVbr;
    }

    public boolean isOpusFec() {
        return opusFec;
    }

    public boolean isOpusMusic() {
        return opusMusic;
    }

    public static OpusParameters fromPluginConfiguration(Plugin plugin) throws PluginException {
        return new OpusParameters(
                Integer.parseInt(plugin.getProperty("opusFrameTime", "20")),
                Integer.parseInt(plugin.getProperty("opusBitRate", "96000")),
                Integer.parseInt(plugin.getProperty("opusComplexity", "10")),
                Integer.parseInt(plugin.getProperty("opusPacketLoss", "20")),
                Boolean.parseBoolean(plugin.getProperty("opusVbr", "true")),
                Boolean.parseBoolean(plugin.getProperty("opusFec", "true")),
                Boolean.parseBoolean(plugin.getProperty("opusMusic", "true"))
        );
    }
}