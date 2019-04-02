package io.manebot.plugin.audio.command;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.command.executor.chained.argument.CommandArgumentSwitch;
import io.manebot.conversation.Conversation;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginRegistration;
import io.manebot.plugin.audio.AudioPlugin;
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
        sender.list(
                AudioChannel.class,
                builder -> builder
                        .direct(pluginRegistration.getInstance().getInstance(AudioPlugin.class).getChannels())
                        .page(page)
                        .responder((sender1, o) ->
                                o.getConversation().getId() + ": " +
                                        o.getState().name() + (o.isIdle() ? " (idle)" : ""))
                        .build()
        ).send();
    }

    @Command(description = "Manages mixer audio filter", permission = "audio.filter.change")
    public void filter(CommandSender sender,
                         @CommandArgumentLabel.Argument(label = "filter") String filter,
                         @CommandArgumentSwitch.Argument(labels = {"enable","disable"}) String toggle)
            throws CommandExecutionException {
        Conversation conversation = sender.getConversation();
        if (conversation == null) throw new CommandArgumentException("Unknown conversation.");
        AudioChannel channel = pluginRegistration.getInstance().getInstance(AudioPlugin.class).getChannel(sender);
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
        AudioChannel channel = pluginRegistration.getInstance().getInstance(AudioPlugin.class).getChannel(conversation);
        if (channel == null) throw new CommandArgumentException("Audio channel not found");

        List<AudioPlayer> players = new ArrayList<>(channel.getPlayers());
        players.sort((player, t1) -> -player.getStarted().compareTo(t1.getStarted()));

        sender.list(
                AudioPlayer.class,
                builder -> builder.direct(players).page(page)
                .responder((sender1, o) -> o.getClass().getSimpleName()
                        + " date=" + o.getStarted()
                        + " user=" + o.getOwner().getDisplayName()
                        + " state=[" + (o.isBlocking() ? "blk" : "nonblk")
                        + "," + (o.isPlaying() ? "play" : "stop") + "]")
                .build()
        ).send();
    }


    private void getAudioInfo(CommandSender sender)
            throws CommandExecutionException {
        AudioChannel channel = pluginRegistration.getInstance().getInstance(AudioPlugin.class).getChannel(sender);
        getAudioInfo(sender, channel);
    }

    private void getAudioInfo(CommandSender sender, Conversation conversation)
            throws CommandExecutionException {
        AudioChannel channel = pluginRegistration.getInstance().getInstance(AudioPlugin.class).getChannel(conversation);
        if (channel == null)
            throw new CommandArgumentException("Conversation does not have an associated audio channel");

        getAudioInfo(sender, channel);
    }

    private void getAudioInfo(CommandSender sender, AudioChannel channel)
            throws CommandExecutionException {
        if (channel == null) throw new CommandArgumentException("Audio channel not found");

        List<UserAssociation> listeners = channel.getRegisteredListeners();
        sender.details(
                builder -> builder.name("Audio channel").key(channel.getId())
                        .item("Instance", channel.getClass().getSimpleName())
                        .item("Channel state", channel.getState().name() +  (channel.isIdle() ? " (idle)" : ""))
                        .item("Audio players",channel.getPlayers().size() +
                                " (" + channel.getBlockingPlayers() + " blocking)")
                        .item("Members", listeners.stream().map(x ->
                                x.getUser().getDisplayName() + (channel.isSpeaking(x) ? " (speaking)" :"")
                        ).collect(Collectors.toList()))
                        .build()
        ).send();
    }
}
