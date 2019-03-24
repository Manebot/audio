package com.github.manevolent.jbot.plugin.audio;

import com.github.manevolent.jbot.conversation.Conversation;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.PluginException;
import com.github.manevolent.jbot.plugin.PluginReference;
import com.github.manevolent.jbot.plugin.audio.channel.AudioChannel;
import com.github.manevolent.jbot.plugin.audio.mixer.BufferedMixer;
import com.github.manevolent.jbot.plugin.audio.mixer.Mixer;
import com.github.manevolent.jbot.plugin.audio.mixer.MixerRegistrant;
import com.github.manevolent.jbot.plugin.audio.mixer.filter.type.*;
import com.github.manevolent.jbot.plugin.audio.mixer.input.MixerChannel;
import com.github.manevolent.jbot.plugin.audio.mixer.output.MixerSink;
import com.github.manevolent.jbot.plugin.audio.mixer.output.NativeMixerSink;
import com.github.manevolent.jbot.plugin.audio.player.AudioPlayer;
import com.github.manevolent.jbot.plugin.audio.resample.FFmpegResampler;
import com.github.manevolent.jbot.plugin.audio.resample.ResamplerFactory;
import com.github.manevolent.jbot.plugin.audio.util.LoopTimer;
import com.github.manevolent.jbot.security.Permission;
import com.github.manevolent.jbot.virtual.Profiler;
import com.github.manevolent.jbot.virtual.Virtual;

import javax.sound.sampled.AudioFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AudioPlugin implements PluginReference, Runnable, MixerRegistrant {
    public static final Permission AUDIO_PLAY_PERMISSION = Permission.get("system.audio.play");

    private final Object audioLock = new Object();
    private final List<AudioChannel> channels = new LinkedList<>();
    private final List<Mixer> mixers = new LinkedList<>();
    private final Map<String, List<AudioChannel.Listener>> audioChannelListeners = new HashMap<>();
    private final List<AudioChannel.Listener> globalListeners = new LinkedList<>();

    private Plugin plugin;
    private volatile Mixer nativeMixer;
    private long bufferTime;
    private long loopDelay;
    private int bufferSize;
    private AudioFormat format;
    private ResamplerFactory resamplerFactory;

    public Mixer getNativeMixer() {
        return nativeMixer;
    }

    public List<Mixer> getMixers() {
        return Collections.unmodifiableList(mixers);
    }

    public List<AudioChannel> getAudioChannels() {
        return Collections.unmodifiableList(channels);
    }

    public AudioFormat getNativeFormat() {
        return format;
    }

    public ResamplerFactory getResamplerFactory() {
        return resamplerFactory;
    }

    public long getLoopDelay() {
        return loopDelay;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    // (Bytes)
    public int getBufferSize(AudioFormat format) {
        float seconds = (float)bufferTime / 1000f;
        int bytesPerSecond = (int) (format.getFrameRate() * format.getFrameSize());
        int ret = (int) Math.floor(bytesPerSecond * seconds);
        if (ret != (bytesPerSecond * seconds))
            throw new IllegalArgumentException("Bad buffer size: " + ret + " != " + (bytesPerSecond * seconds));
        return ret;
    }

    public int getBufferSizeInSamples(AudioFormat format) {
        return getBufferSize(format) / (format.getSampleSizeInBits()/8);
    }

    public void registerChannel(AudioChannel channel) {
        if (this.channels.add(channel)) {
            List<AudioChannel.Listener> localListeners = getAudioChannelListeners(channel.getId());
            synchronized (localListeners) {
                for (AudioChannel.Listener listener : localListeners) {
                    if (!channel.getListeners().contains(listener))
                        channel.registerListener(listener);
                }
            }

            synchronized (globalListeners) {
                for (AudioChannel.Listener listener : globalListeners) {
                    if (!channel.getListeners().contains(listener))
                        channel.registerListener(listener);
                }
            }

            channel.getRegistrant().onChannelRegistered(channel);
        }
    }

    public boolean unregisterChannel(AudioChannel channel) {
        if (this.channels.remove(channel)) {
            channel.getRegistrant().onChannelUnregistered(channel);
            channel.getListeners().forEach(channel::unregisterListener);
            channel.stopAll();

            return true;
        } else return false;
    }

    public AudioChannel getChannelById(String channelId) {
        return channels.stream().filter(x -> x.getId().equals(channelId)).findFirst().orElse(null);
    }

    public AudioChannel getChannel(Conversation conversation) {
        return getChannelById(conversation.getId());
    }

    public List<AudioChannel.Listener> getAudioChannelListeners(String channelId) {
        List<AudioChannel.Listener> listeners = audioChannelListeners.get(channelId);
        if (listeners == null) return new LinkedList<>();
        else return listeners;
    }

    public void registerAudioChannelListener(AudioChannel.Listener listener) {
        synchronized (globalListeners) {
            if (globalListeners.add(listener)) {
                for (AudioChannel channel : new ArrayList<>(getChannels())) {
                    channel.registerListener(listener);
                    plugin.getLogger().log(Level.FINE, "Unregistered audio channel listener for channel " + channel.getId());
                }
            }
        }
    }

    public void unregisterAudioChannelListener(AudioChannel.Listener listener) {
        synchronized (globalListeners) {
            if (globalListeners.remove(listener)) {
                for (AudioChannel channel : new ArrayList<>(getChannels())) {
                    channel.unregisterListener(listener);
                    plugin.getLogger().log(Level.FINE, "Unregistered audio channel listener for channel " + channel.getId());
                }
            }
        }
    }

    public void registerAudioChannelListener(String channelId, AudioChannel.Listener listener) {
        synchronized (audioChannelListeners) {
            List<AudioChannel.Listener> listeners = getAudioChannelListeners(channelId);
            if (!listeners.contains(listener) && listeners.add(listener))
                plugin.getLogger().log(Level.FINE, "Registered audio channel listener for channel " + channelId);

            AudioChannel channel = getChannelById(channelId);
            if (channel != null) channel.registerListener(listener);

            audioChannelListeners.put(channelId, listeners);
        }
    }

    public void unregisterAudioChannelListener(String channelId, AudioChannel.Listener listener) {
        synchronized (audioChannelListeners) {
            List<AudioChannel.Listener> listeners = getAudioChannelListeners(channelId);
            if (listeners.remove(listener)) {
                AudioChannel channel = getChannelById(channelId);
                if (channel != null) channel.unregisterListener(listener);

                if (listeners.size() <= 0) audioChannelListeners.remove(channelId); // Cleanup

                plugin.getLogger().log(Level.FINE, "Unregistered audio channel listener for channel " + channelId);
            }
        }
    }

    public List<AudioChannel> getChannels() {
        return channels;
    }

    public final Mixer createMixer(MixerSink sink) {
        return createMixer(
                "mixer:" + System.currentTimeMillis(),
                sink,
                getBufferSizeInSamples(sink.getAudioFormat())
        );
    }

    public final Mixer createMixer(MixerSink sink, int bufferSizeInSamples) {
        return createMixer("mixer:" + System.currentTimeMillis(), sink, bufferSizeInSamples);
    }

    public final Mixer createMixer(String id, MixerSink sink) {
        return createMixer(id, sink, getBufferSizeInSamples(sink.getAudioFormat()));
    }

    public final Mixer createMixer(String id, MixerSink sink, int bufferSizeInSamples) {
        BufferedMixer mixer = new BufferedMixer(
                id,
                this,
                bufferSizeInSamples,
                sink.getAudioFormat().getSampleRate(),
                sink.getAudioFormat().getChannels()
        );

        mixer.addSink(sink);

        ///
        /// MASTER FILTER CHAIN
        ///

        // Compress
        float compressorTheshold = Float.parseFloat(plugin.getProperty("compressorTheshold", "1"));
        float compressorRatio = Float.parseFloat(plugin.getProperty("compressorRatio", "1"));
        float compressorKnee = Float.parseFloat(plugin.getProperty("compressorKnee", "0"));
        mixer.addFilter(
                new FilterCompressor(compressorTheshold, compressorRatio, compressorKnee),
                new FilterCompressor(compressorTheshold, compressorRatio, compressorKnee)
        );

        // Bass boost
        float subBassFrequency = Float.parseFloat(plugin.getProperty("subBassFrequency", "65.0"));
        float subBassResonance = Float.parseFloat(plugin.getProperty("subBassResonance", "1"));
        float subBassWet = Float.parseFloat(plugin.getProperty("subBassWet", "0.35"));
        float subBassDry = Float.parseFloat(plugin.getProperty("subBassDry", "0.65"));
        SoftFilter subBassFilter = new SoftFilter(sink.getAudioFormat().getSampleRate());
        subBassFilter.setFilterType(SoftFilter.FILTERTYPE_BP12);
        subBassFilter.setFrequency(subBassFrequency);
        subBassFilter.setResonance(subBassResonance);
        mixer.addFilter(
                new FilterBandPass(subBassFilter, subBassWet, subBassDry),
                new FilterBandPass(subBassFilter, subBassWet, subBassDry)
        );

        // Bass boost
        float bassFrequency = Float.parseFloat(plugin.getProperty("bassFrequency", "120.0"));
        float bassResonance = Float.parseFloat(plugin.getProperty("bassResonance", "1"));
        float bassWet = Float.parseFloat(plugin.getProperty("bassWet", "0.5"));
        float bassDry = Float.parseFloat(plugin.getProperty("bassDry", "0.5"));
        SoftFilter bassFilter = new SoftFilter(sink.getAudioFormat().getSampleRate());
        bassFilter.setFilterType(SoftFilter.FILTERTYPE_BP12);
        bassFilter.setFrequency(bassFrequency);
        bassFilter.setResonance(bassResonance);
        mixer.addFilter(
                new FilterBandPass(bassFilter, bassWet, bassDry),
                new FilterBandPass(bassFilter, bassWet, bassDry)
        );

        // Mid/vocal boost
        float midFrequency = Float.parseFloat(plugin.getProperty("midFrequency", "2500"));
        float midResonance = Float.parseFloat(plugin.getProperty("midResonance", "1"));
        float midWet = Float.parseFloat(plugin.getProperty("midWet", "0.15"));
        float midDry = Float.parseFloat(plugin.getProperty("midDry", "0.85"));
        SoftFilter midFilter = new SoftFilter(sink.getAudioFormat().getSampleRate());
        midFilter.setFilterType(SoftFilter.FILTERTYPE_BP12);
        midFilter.setFrequency(midFrequency);
        midFilter.setResonance(midResonance);
        mixer.addFilter(
                new FilterBandPass(midFilter, midWet, midDry),
                new FilterBandPass(midFilter, midWet, midDry)
        );

        float limiterThreshold = Float.parseFloat(plugin.getProperty("limiterThreshold", "0.7"));
        float limiterAttack = Float.parseFloat(plugin.getProperty("limiterAttack", "1"));
        float limiterRelease = Float.parseFloat(plugin.getProperty("limiterRelease", "0.0001"));
        float limiterSlope = Float.parseFloat(plugin.getProperty("limiterSlope", "0.5"));

        // Master limiter
        mixer.addFilter(
                new FilterLimiter(limiterThreshold, limiterAttack, limiterRelease, limiterSlope),
                new FilterLimiter(limiterThreshold, limiterAttack, limiterRelease, limiterSlope)
        );

        float masterGain = Float.parseFloat(plugin.getProperty("masterGain", "0.99"));

        // Headroom gain (To prevent use of a solid +1 or -1)
        mixer.addFilter(new FilterGain(masterGain), new FilterGain(masterGain));

        // Dither (To allow use of +1 or -1)
        mixer.addFilter(
                new FilterDither(sink.getAudioFormat().getSampleSizeInBits()),
                new FilterDither(sink.getAudioFormat().getSampleSizeInBits())
        );

        plugin.getLogger().fine("Created new mixer wrapping sink class: " +
                sink.getClass().getName()
                + " (format=" + sink.getAudioFormat() + ";" + "buffer=" + bufferSizeInSamples +"smp)"
        );

        synchronized (audioLock) {
            if (mixers.stream().anyMatch(x -> x.getId().equalsIgnoreCase(id)))
                throw new IllegalArgumentException("Mixer already exists: " + id);

            mixers.add(mixer);
            audioLock.notifyAll();
        }

        mixer.getRegistrant().onMixerRegistered(mixer);

        return mixer;
    }

    public boolean unregisterMixer(Mixer mixer) {
        if (mixers.remove(mixer)) {
            mixer.getRegistrant().onMixerUnregistered(mixer);

            // Make sure they cleaned everything up
            mixer.empty();

            return true;
        } else
            return false;
    }

    @Override
    public void load(Plugin.Future future) {
        int sampleRate = Integer.parseInt(future.getPlugin().getProperty("sampleRate", "48000"));
        int sampleSize = Integer.parseInt(future.getPlugin().getProperty("sampleBits", "16"));
        int channels = Integer.parseInt(future.getPlugin().getProperty("channels", "2"));

        bufferTime = Integer.parseInt(future.getPlugin().getProperty("delay", "500"));
        loopDelay = Integer.parseInt(future.getPlugin().getProperty("loopDelay",
                Integer.toString((int) bufferTime / 10)));

        resamplerFactory = new FFmpegResampler.FFmpegResamplerFactory();

        this.format = new AudioFormat(sampleRate, sampleSize, channels, true, false);
        bufferSize = getBufferSizeInSamples(format);

        future.getPlugin().getLogger().info("Starting Audio subsystem with " + sampleRate + "Hz, " +
                sampleSize + "bit " + channels + "ch format (" + bufferTime + "ms delay, " +
                (int)(1000.0D / loopDelay) + "Hz update rate)...");

        try {
            this.nativeMixer = createMixer("native", new NativeMixerSink(format, bufferSize));

            plugin.getRegistration().getInstance()
                    .getLogger().fine("Native audio line opened, testing output...");

            try {
                this.nativeMixer.setRunning(true);
                this.nativeMixer.setRunning(false);
            } catch (Exception ex) {
                throw new PluginException(ex);
            }
        } catch (Exception e) {
            plugin.getRegistration().getInstance()
                    .getLogger().warning("Failed to open native audio device! Continuing without system audio...");
        }

        future.getPlugin().getLogger().info("Audio subsystem started.");

        this.plugin = future.getPlugin();

        future.afterAsync((registration -> this.run()));
    }

    @Override
    public void unload(Plugin.Future future) {
        // Stop all channels.
        Iterator<AudioChannel> channelIterator = channels.iterator();
        while (channelIterator.hasNext()) {
            channelIterator.next().getPlayers().forEach(AudioPlayer::stop);
            channelIterator.remove();
        }

        // Notify main loop
        synchronized (audioLock) {
            mixers.clear();
            audioLock.notifyAll();
        }
    }

    @Override
    public void run() {
        try {
            Virtual.getInstance().currentProcess().setDescription("AudioThread");

            LoopTimer timer = new LoopTimer(getLoopDelay(), audioLock);

            List<Mixer> playingMixers = new ArrayList<>();

            synchronized (audioLock) {
                while (plugin.isEnabled()) {
                    try (Profiler audioProfiler = Profiler.region("audio")) {
                        for (Mixer mixer : mixers) {
                            // If the mixer isn't playing anything we have no work to do.
                            if (!mixer.isPlaying()) {
                                // If mixer is running, stop the mixer since no players are playing.
                                if (mixer.isRunning()) {
                                    Logger.getGlobal().fine("Stopping mixer: " + mixer.getId() + "...");

                                    Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
                                    mixer.setRunning(false);

                                    Logger.getGlobal().fine("Stopped mixer: " + mixer.getId() + ".");
                                }
                            } else {
                                playingMixers.add(mixer);
                            }
                        }

                        if (playingMixers.size() <= 0) {
                            audioLock.wait(1000L); // I see the point now
                            continue;
                        }

                        for (Mixer mixer : playingMixers) {
                            // Play audio on system
                            try {
                                // Start the mixer if it's not running
                                if (!mixer.isRunning()) {
                                    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                                    mixer.setRunning(true);
                                }

                                mixer.processBuffer();
                            } catch (Exception ex) {
                                plugin.getLogger().log(Level.SEVERE, "Problem processing Mixer buffer; emptying mixer", ex);

                                mixer.empty();
                            }
                        }

                        // Clear the list to wipe clean
                        playingMixers.clear();

                        // Sleep for designated amount of time
                        try (Profiler sleepProfiler = Profiler.region("sleep")) {
                            timer.sleep();
                        } catch (InterruptedException e) {
                            Thread.yield();
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Problem in AudioPlugin processor main thread; shutting down audio", ex);
        }
    }

    @Override
    public void onMixerStarted(Mixer mixer) {
        plugin.getLogger().fine("Mixer started playback: starting sink.");

        synchronized (audioLock) {
            audioLock.notifyAll();
        }
    }

    /**
     * Called by the mixer
     */
    @Override
    public void onMixerStopped(Mixer mixer) {
        plugin.getLogger().fine("Mixer stopped playback: stopping sink.");
    }

    @Override
    public void onChannelAdded(Mixer mixer, MixerChannel channel) {
        plugin.getLogger().fine("Mixer added channel: " + channel.toString() + ".");
    }

    @Override
    public void onChannelRemoved(Mixer mixer, MixerChannel channel) {
        plugin.getLogger().fine("Mixer removed channel: " + channel.toString() + ".");
    }

    @Override
    public void onChannelSleep(Mixer mixer, AudioChannel channel) {
        
    }

    @Override
    public void onChannelWake(Mixer mixer, AudioChannel channel) {
        
    }
}
