package io.manebot.plugin.audio.command;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentNumeric;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginRegistration;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.audio.mixer.input.MixerChannel;
import io.manebot.plugin.audio.mixer.output.MixerSink;
import io.manebot.security.Permission;

import java.util.ArrayList;
import java.util.Collection;
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
        Mixer mixer = pluginRegistration
                .getInstance()
                .getInstance(Audio.class)
                .getMixer(sender);

        if (mixer == null) throw new CommandArgumentException("There is no mixer associated with this chat.");

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
        Mixer mixer = pluginRegistration
                .getInstance()
                .getInstance(Audio.class)
                .getMixer(sender);

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

    @Command(description = "Lists mixer channels for the current sink", permission = "audio.mixer.sinks")
    public void sinks(CommandSender sender,
                      @CommandArgumentLabel.Argument(label = "sinks") String sinks,
                      @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        getMixerSinks(
                sender,
                page
        );
    }

    @Command(description = "Lists mixer channels for the a specific sink", permission = "audio.mixer.sinks")
    public void sinks(CommandSender sender,
                      @CommandArgumentLabel.Argument(label = "sinks") String sinks,
                      @CommandArgumentString.Argument(label = "id") String id,
                      @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        getMixerSinks(
                sender,
                getMixerById(id),
                page
        );
    }

    @Command(description = "Changes mixer volume for your players", permission = "audio.mixer.volume")
    public void volume(CommandSender sender,
                      @CommandArgumentLabel.Argument(label = "volume") String label,
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
        return pluginRegistration.getInstance().getInstance(Audio.class).getMixers().stream()
                .filter(x -> x.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new CommandArgumentException("Mixer not found."));
    }

    private void getMixerChannels(CommandSender sender, Mixer mixer, int page)
            throws CommandExecutionException {
        sender.sendList(
                MixerChannel.class,
                builder -> builder.direct(new ArrayList<>(mixer.getChannels())).page(page)
                .responder((textBuilder,c) -> textBuilder
                        .append(c.getName())
                        .append(" (" + c.getClass().getSimpleName() + ", "
                                + (c.isPlaying() ? "playing, " : "")
                                + c.available() + " av)"))
        );
    }

    private void getMixers(CommandSender sender, int page)
            throws CommandExecutionException {
        List<Mixer> mixers = pluginRegistration.getInstance().getInstance(Audio.class).getMixers();

        sender.sendList(
                Mixer.class,
                builder -> builder.direct(mixers).page(page)
                        .responder((textBuilder,c) -> textBuilder
                                .append(c.getId())
                                .append(" (" + c.getClass().getSimpleName() + ", "
                                        + (c.isRunning() ? "running, " : "stopped, ")
                                        + (c.isPlaying() ? "playing, " : "paused, ")
                                        + c.getBufferSize() + " smp, "
                                        + c.available() + " av, "
                                        + c.getSinks().size() + " snk, "
                                        + c.getChannels().size() + " ch, "
                                        + String.format("%.3f", c.getPositionInSeconds()) + " sec"
                                        + ")")
                        )
        );
    }

    private void getMixerSinks(CommandSender sender, int page)
            throws CommandExecutionException {
        Mixer mixer = pluginRegistration
                .getInstance()
                .getInstance(Audio.class)
                .getMixer(sender);

        getMixerSinks(sender, mixer, page);
    }

    private void getMixerSinks(CommandSender sender, Mixer mixer, int page)
            throws CommandExecutionException {
        sender.sendList(
                MixerSink.class,
                builder -> builder.direct(new ArrayList<>(mixer.getSinks())).page(page)
                        .responder((textBuilder,c) -> textBuilder
                                .append(c.getClass().getName())
                                .append(" (")
                                .append(c.isRunning() ? "running, " : "stopped, ")
                                .append(c.getBufferSize() + " smp, ")
                                .append(c.availableInput() + " av, ")
                                .append(String.format("%.3f",
                                    getPositionInSeconds(c.getPosition(), c.getAudioFormat())) + " sec, "
                                + c.getUnderflows() + " uf, "
                                + c.getOverflows() + " of")
                                .append(")")
                        )
        );
    }

    private double getPositionInSeconds(long position, javax.sound.sampled.AudioFormat format) {
        return (double)position / (double)(format.getSampleRate()*format.getChannels());
    }

    private void getMixerInfo(CommandSender sender) throws CommandExecutionException {
        Mixer mixer = pluginRegistration
                .getInstance()
                .getInstance(Audio.class)
                .getMixer(sender);

        getMixerInfo(sender, mixer);
    }

    private void getMixerInfo(CommandSender sender, Mixer mixer) throws CommandExecutionException {
        sender.sendDetails(
                builder -> builder.name("Mixer").key(mixer.getId())
                        .item("Instance", mixer.getClass().getSimpleName())
                        .item("Played", String.format("%.3f", mixer.getPositionInSeconds())
                                + " second(s), " + mixer.available() + " av")
                        .item("Buffer size", mixer.getBufferSize() + " sample(s)")
        );
    }
}
