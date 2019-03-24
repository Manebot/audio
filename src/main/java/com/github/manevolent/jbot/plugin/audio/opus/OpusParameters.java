package com.github.manevolent.jbot.plugin.audio.opus;

public class OpusParameters {
    private final int opusFrameRate;
    private final int opusBitrate;
    private final int opusComplexity;
    private final int opusPacketLossPercent;
    private final boolean opusVbr;
    private final boolean opusFec;
    private final boolean opusMusic;

    public OpusParameters(int opusFrameRate, int opusBitrate,
                          int opusComplexity, int opusPacketLossPercent,
                          boolean opusVbr, boolean opusFec, boolean opusMusic) {
        this.opusFrameRate = opusFrameRate;
        this.opusBitrate = opusBitrate;
        this.opusComplexity = opusComplexity;
        this.opusPacketLossPercent = opusPacketLossPercent;
        this.opusVbr = opusVbr;
        this.opusFec = opusFec;
        this.opusMusic = opusMusic;
    }

    public int getOpusFrameTime() {
        return 1000 / opusFrameRate;
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
}