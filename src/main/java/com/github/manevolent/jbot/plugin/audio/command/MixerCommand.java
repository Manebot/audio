package com.github.manevolent.jbot.plugin.audio.command;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentLabel;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentNumeric;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentPage;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.PluginRegistration;
import com.github.manevolent.jbot.plugin.audio.AudioPlugin;
import com.github.manevolent.jbot.plugin.audio.channel.AudioChannel;
import com.github.manevolent.jbot.plugin.audio.mixer.Mixer;
import com.github.manevolent.jbot.plugin.audio.mixer.input.MixerChannel;
import com.github.manevolent.jbot.plugin.audio.mixer.output.MixerSink;
import com.github.manevolent.jbot.security.Permission;

import java.util.ArrayList;
import java.util.List;

public class MixerCommand extends AnnotatedCommandExecutor {
    private final PluginRegistration pluginRegistration;

    public MixerCommand(Plugin.Future future) {
        this.pluginRegistration = future.getRegistration();
    }

    @Command(description = "Gets current mixer information", permission = "audio.mixer.info")
    public void info(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "info") String players,
                        @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        AudioChannel channel = pluginRegistration
                .getInstance()
                .getInstance(AudioPlugin.class)
                .getChannel(sender.getConversation());
        Mixer mixer = channel.getMixer();
        getMixerInfo(sender, mixer);
    }

    @Command(description = "Gets another mixer's information", permission = "audio.mixer.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String players,
                     @CommandArgumentString.Argument(label = "id") String id,
                     @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        getMixerInfo(sender, getMixerById(id));
    }

    @Command(description = "Lists mixer channels for the current channel", permission = "audio.mixer.channels")
    public void channels(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "channels") String players,
                     @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        AudioChannel channel = pluginRegistration
                .getInstance()
                .getInstance(AudioPlugin.class)
                .getChannel(sender.getConversation());
        Mixer mixer = channel.getMixer();
        getMixerChannels(sender, mixer, page);
    }

    @Command(description = "Lists mixer channels for the another mixer", permission = "audio.mixer.channels")
    public void channels(CommandSender sender,
                         @CommandArgumentLabel.Argument(label = "channels") String players,
                         @CommandArgumentLabel.Argument(label = "id") String id,
                         @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        getMixerChannels(sender, getMixerById(id), page);
    }

    @Command(description = "Lists mixer channels for the another mixer", permission = "audio.mixer.list")
    public void list(CommandSender sender,
                         @CommandArgumentLabel.Argument(label = "list") String list,
                         @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        getMixers(sender, page);
    }

    @Command(description = "Lists mixer channels for the sink", permission = "audio.mixer.sinks")
    public void sinks(CommandSender sender,
                      @CommandArgumentLabel.Argument(label = "sinks") String sinks,
                      @CommandArgumentString.Argument(label = "id") String id,
                      @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        AudioChannel channel = pluginRegistration
                .getInstance()
                .getInstance(AudioPlugin.class)
                .getChannel(sender.getConversation());
        Mixer mixer = channel.getMixer();
        getMixerSinks(
                sender,
                mixer,
                page
        );
    }

    @Command(description = "Lists mixer channels for the sink", permission = "audio.mixer.sinks")
    public void sinks(CommandSender sender,
                      @CommandArgumentLabel.Argument(label = "volume") String sinks,
                      @CommandArgumentNumeric.Argument() int volume)
            throws CommandExecutionException {
        double value = (double) volume;
        if (value < 10D) throw new CommandArgumentException("Mixer volume cannot be lower than 10%.");
        else if (value > 100D) {
            if (!sender.getUser().hasPermission(Permission.get("system.audio.volume.loud")))
                throw new CommandArgumentException("Mixer volume cannot be higher than 100%.");

            if (value > 100000D)
                throw new CommandArgumentException("Mixer volume cannot be higher than 100000%.");
        }

        sender.getUser().getEntity().getPropery("Mixer:Volume").set(value / 100D);
        sender.sendMessage("Mixer volume set to " + String.format("%.2f", value) + "%.");
    }

    @Override
    public String getDescription() {
        return "Controls the audio mixer";
    }

    private Mixer getMixerById(String id) throws CommandArgumentException {
        return pluginRegistration.getInstance().getInstance(AudioPlugin.class).getMixers().stream()
                .filter(x -> x.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new CommandArgumentException("Mixer not found."));
    }

    private void getMixerChannels(CommandSender sender, Mixer mixer, int page)
            throws CommandExecutionException {
        sender.list(
                MixerChannel.class,
                builder -> builder.direct(new ArrayList<>(mixer.getChannels())).page(page)
                .responder((x,c) ->
                        c.getName()
                                + " (" + c.getClass().getSimpleName() + ", "
                                + (c.isPlaying() ? "playing, " : "")
                                + c.available() + " av)")
                .build()
        ).send();
    }

    private void getMixers(CommandSender sender, int page)
            throws CommandExecutionException {
        List<Mixer> mixers = pluginRegistration
                .getInstance()
                .getInstance(AudioPlugin.class)
                .getMixers();

        sender.list(
                Mixer.class,
                builder -> builder.direct(mixers).page(page)
                        .responder((x,c) ->
                                c.getId() + " (" + c.getClass().getSimpleName() + ", "
                                        + (c.isRunning() ? "running, " : "stopped, ")
                                        + (c.isPlaying() ? "playing, " : "paused, ")
                                        + c.getBufferSize() + " smp, "
                                        + c.available() + " av, "
                                        + c.getSinks().size() + " snk, "
                                        + c.getChannels().size() + " ch, "
                                        + String.format("%.3f", c.getPositionInSeconds()) + " sec"
                                        + ")"
                        ).build()
        ).send();
    }

    private void getMixerSinks(CommandSender sender, Mixer mixer, int page)
            throws CommandExecutionException {
        sender.list(
                MixerSink.class,
                builder -> builder.direct(new ArrayList<>(mixer.getSinks())).page(page)
                        .responder((x,c) -> c.getClass().getName()
                                + " ("
                                + (c.isRunning() ? "running, " : "stopped, ")
                                + c.getBufferSize() + " smp, "
                                + c.availableInput() + " av, "
                                + String.format("%.3f",
                                    getPositionInSeconds(c.getPosition(), c.getAudioFormat())) + " sec, "
                                + c.getUnderflows() + " uf, "
                                + c.getOverflows() + " of"
                                + ")"
                        ).build()
        ).send();
    }

    private double getPositionInSeconds(long position, javax.sound.sampled.AudioFormat format) {
        return (double)position / (double)(format.getSampleRate()*format.getChannels());
    }

    private void getMixerInfo(CommandSender sender, Mixer mixer) throws CommandExecutionException {
        sender.details(
                builder -> builder.name("Mixer").key(mixer.getId())
                        .item("Instance", mixer.getClass().getSimpleName())
                        .item("Played", String.format("%.3f", mixer.getPositionInSeconds())
                                + " second(s), " + mixer.available() + " av")
                        .item("Buffer size", mixer.getBufferSize() + " sample(s)")
                        .build()
        ).send();
    }
}
