package com.github.manevolent.jbot.plugin.audio.command;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentLabel;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentPage;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentSwitch;
import com.github.manevolent.jbot.conversation.Conversation;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.PluginRegistration;
import com.github.manevolent.jbot.plugin.audio.AudioPlugin;
import com.github.manevolent.jbot.plugin.audio.channel.AudioChannel;
import com.github.manevolent.jbot.plugin.audio.mixer.Mixer;
import com.github.manevolent.jbot.plugin.audio.player.AudioPlayer;
import com.github.manevolent.jbot.user.User;

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
        getAudioInfo(sender, sender.getConversation());
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
        AudioChannel channel = pluginRegistration.getInstance().getInstance(AudioPlugin.class).getChannel(conversation);
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

    private void getAudioInfo(CommandSender sender, Conversation conversation)
            throws CommandExecutionException {
        AudioChannel channel = pluginRegistration.getInstance().getInstance(AudioPlugin.class).getChannel(conversation);
        List<User> listeners = channel.getActiveListeners();
        sender.details(
                builder -> builder.name("Audio channel").key(channel.getId())
                        .item("Instance", channel.getClass().getSimpleName())
                        .item("Channel state", channel.getState().name() +  (channel.isIdle() ? " (idle)" : ""))
                        .item("Audio players",channel.getPlayers().size() +
                                " (" + channel.getBlockingPlayers() + " blocking)")
                        .item("Members", listeners.stream().map(x ->
                                x.getDisplayName() + (channel.isSpeaking(x) ? " (speaking)" :"")
                        ).collect(Collectors.toList()))
                        .build()
        ).send();
    }
}
