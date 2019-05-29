package io.manebot.plugin.audio;

import io.manebot.chat.Chat;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.conversation.Conversation;
import io.manebot.platform.Platform;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginReference;
import io.manebot.plugin.audio.api.AudioRegistration;
import io.manebot.plugin.audio.api.DefaultAudioRegistration;

import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.mixer.BufferedMixer;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.audio.mixer.filter.MultiChannelFilter;
import io.manebot.plugin.audio.mixer.filter.MuxedMultiChannelFilter;
import io.manebot.plugin.audio.mixer.filter.SoftFilter;
import io.manebot.plugin.audio.mixer.filter.type.*;

import io.manebot.plugin.audio.resample.FFmpegResampler;
import io.manebot.plugin.audio.resample.ResamplerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Audio implements PluginReference {
    private final Plugin plugin;
    private final Map<Platform, AudioRegistration> registrationMap = new LinkedHashMap<>();

    private long bufferTime;
    private long loopDelay;
    private ResamplerFactory resamplerFactory;

    Audio(Plugin plugin) {
        this.plugin = plugin;
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

        for (AudioRegistration registration : new ArrayList<>(registrationMap.values()))
            registration.getConnection().connect();

        future.getPlugin().getLogger().info("Audio subsystem loaded.");
    }

    @Override
    public void unload(Plugin.Future future) {
        for (AudioRegistration registration : new ArrayList<>(registrationMap.values()))
            registration.getConnection().disconnect();
    }

    public AudioChannel requireListening(CommandSender sender) throws CommandExecutionException {
        AudioChannel channel = getChannel(sender);

        if (channel == null)
            throw new CommandExecutionException("There is no audio channel associated with this conversation.");

        if (!channel.getListeners().contains(sender.getPlatformUser()) &&
                !sender.getUser().hasPermission("audio.listen.bypass"))
            throw new CommandArgumentException("You are not listening to this channel.");

        return channel;
    }

    public AudioRegistration createRegistration(Platform platform, Consumer<AudioRegistration.Builder> consumer) {
        DefaultAudioRegistration.Builder builder = new DefaultAudioRegistration.Builder(this, platform);
        consumer.accept(builder);

        DefaultAudioRegistration registration = builder.build();

        if (plugin.isEnabled()) registration.getConnection().connect();

        registrationMap.put(platform, registration);

        return registration;
    }

    public AudioRegistration removeRegistration(Platform platform) {
        AudioRegistration registration = registrationMap.remove(platform);
        registration.getConnection().disconnect();
        return registration;
    }

    public List<AudioRegistration> getRegistrations() {
        return Collections.unmodifiableList(new ArrayList<>(registrationMap.values()));
    }

    public ResamplerFactory getResamplerFactory() {
        return resamplerFactory;
    }

    public long getLoopDelay() {
        return loopDelay;
    }

    public AudioRegistration getRegistration(Platform platform) {
        return registrationMap.get(platform);
    }

    public Mixer getMixer(Chat chat) {
        AudioRegistration registration = registrationMap.get(chat.getPlatform());
        if (registration == null) return null;
        return registration.getConnection().getMixer(chat);
    }

    public Mixer getMixer(Conversation conversation) {
        return getMixer(conversation.getChat());
    }

    public Mixer getMixer(CommandSender sender) {
        return getMixer(sender.getConversation());
    }

    public AudioChannel getChannel(Chat chat) {
        AudioRegistration registration = registrationMap.get(chat.getPlatform());
        if (registration == null) return null;
        return registration.getConnection().getChannel(chat);
    }

    public AudioChannel getChannel(Conversation conversation) {
        return getChannel(conversation.getChat());
    }

    public AudioChannel getChannel(CommandSender sender) {
        return getChannel(sender.getConversation());
    }

    public List<Mixer> getMixers() {
        return registrationMap.values().stream()
                .map(AudioRegistration::getConnection)
                .filter(Objects::nonNull)
                .flatMap(connection -> connection.getMixers().stream())
                .collect(Collectors.toList());
    }

    public List<AudioChannel> getChannels() {
        return registrationMap.values().stream()
                .map(AudioRegistration::getConnection)
                .filter(Objects::nonNull)
                .flatMap(connection -> connection.getChannels().stream())
                .collect(Collectors.toList());
    }

    public Collection<Function<Mixer, MultiChannelFilter>> getDefaultFilters(float sampleRate, int channels) {
        return Arrays.asList(
                (mixer) -> {
                    // Compress
                    float compressorTheshold = Float.parseFloat(plugin.getProperty("compressorTheshold", "1"));
                    float compressorRatio = Float.parseFloat(plugin.getProperty("compressorRatio", "1"));
                    float compressorKnee = Float.parseFloat(plugin.getProperty("compressorKnee", "0"));
                    return MuxedMultiChannelFilter.from(channels, (ch) -> new FilterCompressor(
                            sampleRate,
                            compressorTheshold, compressorRatio, compressorKnee
                    ));
                },
                mixer -> {
                    float subBassFrequency = Float.parseFloat(plugin.getProperty("subBassFrequency", "65.0"));
                    float subBassResonance = Float.parseFloat(plugin.getProperty("subBassResonance", "1"));
                    float subBassWet = Float.parseFloat(plugin.getProperty("subBassWet", "0.35"));
                    float subBassDry = Float.parseFloat(plugin.getProperty("subBassDry", "0.65"));

                    SoftFilter subBassFilter = new SoftFilter(sampleRate);
                    subBassFilter.setFilterType(SoftFilter.FILTERTYPE_BP12);
                    subBassFilter.setFrequency(subBassFrequency);
                    subBassFilter.setResonance(subBassResonance);

                    return MuxedMultiChannelFilter.from(channels, (ch) -> new FilterBandPass(
                            sampleRate,
                            subBassFilter, subBassWet, subBassDry
                    ));
                },
                mixer -> {
                    float bassFrequency = Float.parseFloat(plugin.getProperty("bassFrequency", "120.0"));
                    float bassResonance = Float.parseFloat(plugin.getProperty("bassResonance", "1"));
                    float bassWet = Float.parseFloat(plugin.getProperty("bassWet", "0.5"));
                    float bassDry = Float.parseFloat(plugin.getProperty("bassDry", "0.5"));

                    SoftFilter bassFilter = new SoftFilter(sampleRate);
                    bassFilter.setFilterType(SoftFilter.FILTERTYPE_BP12);
                    bassFilter.setFrequency(bassFrequency);
                    bassFilter.setResonance(bassResonance);

                    return MuxedMultiChannelFilter.from(channels, (ch) -> new FilterBandPass(
                            sampleRate,
                            bassFilter, bassWet, bassDry
                    ));
                },
                mixer -> {
                    float midFrequency = Float.parseFloat(plugin.getProperty("midFrequency", "2500"));
                    float midResonance = Float.parseFloat(plugin.getProperty("midResonance", "1"));
                    float midWet = Float.parseFloat(plugin.getProperty("midWet", "0.15"));
                    float midDry = Float.parseFloat(plugin.getProperty("midDry", "0.85"));
                    SoftFilter midFilter = new SoftFilter(mixer.getAudioSampleRate());
                    midFilter.setFilterType(SoftFilter.FILTERTYPE_BP12);
                    midFilter.setFrequency(midFrequency);
                    midFilter.setResonance(midResonance);

                    return MuxedMultiChannelFilter.from(channels, (ch) -> new FilterBandPass(
                            sampleRate,
                            midFilter, midWet, midDry
                    ));
                },
                mixer -> {
                    float limiterThreshold = Float.parseFloat(plugin.getProperty("limiterThreshold", "0.7"));
                    float limiterAttack = Float.parseFloat(plugin.getProperty("limiterAttack", "1"));
                    float limiterRelease = Float.parseFloat(plugin.getProperty("limiterRelease", "0.0001"));
                    float limiterSlope = Float.parseFloat(plugin.getProperty("limiterSlope", "0.5"));

                    return MuxedMultiChannelFilter.from(channels, (ch) -> new FilterLimiter(
                            sampleRate,
                            limiterThreshold, limiterAttack, limiterRelease, limiterSlope
                    ));
                },
                mixer -> MuxedMultiChannelFilter.from(channels, (ch) -> new FilterSoftClip(sampleRate))
        );
    }

    public Mixer createMixer(String id, Consumer<Mixer.Builder> consumer) {
        BufferedMixer.Builder builder = new BufferedMixer.Builder(this, id);
        builder.setBufferTime((float)bufferTime / 1000f);
        consumer.accept(builder);
        return builder.build();
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
