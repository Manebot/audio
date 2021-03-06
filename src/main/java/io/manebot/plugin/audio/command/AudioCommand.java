package io.manebot.plugin.audio.command;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.*;
import io.manebot.conversation.Conversation;
import io.manebot.platform.Platform;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginRegistration;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.api.AudioRegistration;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.audio.player.AudioPlayer;
import io.manebot.user.UserAssociation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AudioCommand extends AnnotatedCommandExecutor {
    private final PluginRegistration pluginRegistration;

    public AudioCommand(Plugin.Future future) {
        this.pluginRegistration = future.getRegistration();
    }

    @Override
    public String getDescription() {
        return "Manages audio subsystem";
    }

    @Command(description = "Gets platforms registered in the audio system", permission = "audio.platform.list")
    public void platforms(CommandSender sender,
                          @CommandArgumentLabel.Argument(label = "platform") String platform,
                          @CommandArgumentLabel.Argument(label = "list") String list,
                          @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        Audio audio = pluginRegistration.getInstance().getInstance(Audio.class);
        sender.sendList(
                AudioRegistration.class,
                builder -> builder
                        .direct(audio.getRegistrations())
                        .page(page)
                        .responder((textBuilder, o) -> textBuilder
                                .append(o.getPlatform().getId())
                                .append(" (via ")
                                .append(o.getPlugin().getName())
                                .append(")"))
        );
    }

    @Command(description = "Gets registrered platform info", permission = "audio.platform.info")
    public void platformInfo(CommandSender sender,
                             @CommandArgumentLabel.Argument(label = "platform") String platformLabel,
                             @CommandArgumentLabel.Argument(label = "info") String list,
                             @CommandArgumentString.Argument(label = "name") String platformId)
            throws CommandExecutionException {
        Platform platform = pluginRegistration.getBot().getPlatformById(platformId);
        if (platform == null) throw new CommandArgumentException("Platform not found.");

        AudioRegistration registration =
                pluginRegistration.getInstance().getInstance(Audio.class).getRegistration(platform);

        if (registration == null) throw new CommandArgumentException("Platform not registered in audio system.");

        sender.sendDetails(
                builder -> builder.name("Platform").key(platform.getId())
                        .item("Plugin", platform.getPlugin().getName())
                        .item("Connected", Boolean.toString(registration.getConnection().isConnected()))
                        .item("Channels", registration.getConnection().getChannels().stream()
                                .map(AudioChannel::getId).collect(Collectors.toList()))
                        .item("Mixers", registration.getConnection().getMixers().stream()
                                .map(Mixer::getId).collect(Collectors.toList()))
        );

    }

    @Command(description = "Gets player information for the current conversation", permission = "audio.player.list")
    public void players(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "players") String players,
                        @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        getAudioPlayers(sender, sender.getConversation(), page);
    }

    @Command(description = "Gets player information for another conversation", permission = "audio.player.list")
    public void players(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "players") String players,
                        @CommandArgumentString.Argument(label = "conversation") String conversation,
                        @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        Conversation c = pluginRegistration.getBot().getConversationProvider().getConversationById(conversation);
        if (conversation == null) throw new CommandArgumentException("Unknown conversation.");
        getAudioPlayers(sender, c, page);
    }

    @Command(description = "Gets player information for the current conversation", permission = "audio.player.info")
    public void info(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "info") String info)
            throws CommandExecutionException {
        getAudioInfo(sender);
    }

    @Command(description = "Gets player information for another conversation", permission = "audio.player.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info,
                     @CommandArgumentString.Argument(label = "conversation") String conversation)
            throws CommandExecutionException {
        Conversation c = pluginRegistration.getBot().getConversationProvider().getConversationById(conversation);
        if (conversation == null) throw new CommandArgumentException("Unknown conversation.");
        getAudioInfo(sender, c);
    }

    @Command(description = "Lists all audio channels", permission = "audio.channel.list")
    public void channels(CommandSender sender,
                         @CommandArgumentLabel.Argument(label = "channels") String channels,
                         @CommandArgumentString.Argument(label = "conversation") String conversation,
                         @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        sender.sendList(
                AudioChannel.class,
                builder -> builder
                        .direct(pluginRegistration.getInstance().getInstance(Audio.class).getChannels())
                        .page(page)
                        .responder((textBuilder, o) -> textBuilder
                                .append(o.getConversation().getId())
                                .append(": ")
                                .append(o.getState().name() + (o.isIdle() ? " (idle)" : "")))
        );
    }

    @Command(description = "Manages mixer audio filter", permission = "audio.filter.change")
    public void filter(CommandSender sender,
                         @CommandArgumentLabel.Argument(label = "filter") String filter,
                         @CommandArgumentSwitch.Argument(labels = {"enable","disable"}) String toggle)
            throws CommandExecutionException {
        Conversation conversation = sender.getConversation();
        if (conversation == null) throw new CommandArgumentException("Unknown conversation.");
        AudioChannel channel = pluginRegistration.getInstance().getInstance(Audio.class).getChannel(sender);
        Mixer mixer = channel.getMixer();
        boolean enable = toggle.equalsIgnoreCase("enable");
        if (mixer.isFiltering() != enable) {
            mixer.setFiltering(enable);
            if (enable)
                sender.sendMessage("Master audio filter enabled for " + conversation.getId() + ".");
            else
                sender.sendMessage("Master audio filter disabled for " + conversation.getId() + ".");
        } else {
            if (enable)
                sender.sendMessage("Master audio filter is already enabled.");
            else
                sender.sendMessage("Master audio filter is already disabled.");
        }

    }

    private void getAudioPlayers(CommandSender sender, Conversation conversation, int page)
            throws CommandExecutionException {
        AudioChannel channel = pluginRegistration.getInstance().getInstance(Audio.class).getChannel(conversation);
        if (channel == null) throw new CommandArgumentException("Audio channel not found");

        List<AudioPlayer> players = new ArrayList<>(channel.getPlayers());
        players.sort((player, t1) -> -player.getStarted().compareTo(t1.getStarted()));

        sender.sendList(
                AudioPlayer.class,
                builder -> builder.direct(players).page(page)
                .responder((textBuilder, o) -> textBuilder
                        .append(o.getClass().getSimpleName())
                        .append(" date=" + o.getStarted())
                        .append(" user=" + o.getOwner().getDisplayName())
                        .append(" state=[" + (o.isBlocking() ? "blk" : "nonblk"))
                        .append("," + (o.isPlaying() ? "play" : "stop") + "]"))
        );
    }


    private void getAudioInfo(CommandSender sender)
            throws CommandExecutionException {
        getAudioInfo(sender, sender.getConversation());
    }

    private void getAudioInfo(CommandSender sender, Conversation conversation)
            throws CommandExecutionException {
        Audio audio = pluginRegistration.getInstance().getInstance(Audio.class);
        if (audio == null)
            throw new CommandArgumentException("Audio subsystem is not initialized.");

        AudioRegistration registration = audio.getRegistration(conversation.getPlatform());
        if (registration == null)
            throw new CommandArgumentException("Platform is not registered to audio subsystem.");

        AudioChannel channel = registration.getConnection().getChannel(conversation.getChat());
        if (channel == null)
            throw new CommandArgumentException("Chat does not have an associated audio channel.");

        getAudioInfo(sender, channel);
    }

    private void getAudioInfo(CommandSender sender, AudioChannel channel)
            throws CommandExecutionException {
        if (channel == null) throw new CommandArgumentException("Audio channel not found.");

        List<UserAssociation> listeners = channel.getRegisteredListeners();
        sender.sendDetails(
                builder -> builder.name("Audio channel").key(channel.getId())
                        .item("Instance", channel.getClass().getSimpleName())
                        .item("Channel state", channel.getState().name() +  (channel.isIdle() ? " (idle)" : ""))
                        .item("Audio players",channel.getPlayers().size() +
                                " (" + channel.getBlockingPlayers() + " blocking)")
                        .item("Members", listeners.stream().map(
                                x -> x.getUser().getDisplayName() +
                                        (channel.isProviding(x.getPlatformUser()) ? " (speaking)" :"")
                        ).collect(Collectors.toList()))
        );
    }
}
