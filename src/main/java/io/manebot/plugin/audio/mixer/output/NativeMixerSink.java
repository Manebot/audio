package io.manebot.plugin.audio.mixer.output;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;

/**
 * Wraps the Java mixer sink.  This sink uses the Java audio layer.  Used by TeamSpeak (+ generic Audio plugin stuff)
 */
public class NativeMixerSink extends JavaMixerSink {
    public NativeMixerSink(AudioFormat format, int bufferSize) throws LineUnavailableException {
        super(AudioSystem.getSourceDataLine(format), bufferSize);
    }
}
