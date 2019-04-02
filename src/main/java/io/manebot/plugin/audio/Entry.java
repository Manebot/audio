package io.manebot.plugin.audio;

import io.manebot.artifact.ManifestIdentifier;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginLoadException;
import io.manebot.plugin.PluginType;
import io.manebot.plugin.audio.command.AudioCommand;
import io.manebot.plugin.audio.command.MixerCommand;
import io.manebot.plugin.java.PluginEntry;

public class Entry implements PluginEntry {
    @Override
    public Plugin instantiate(Plugin.Builder builder) throws PluginLoadException {
        builder.type(PluginType.DEPENDENCY);
        builder.requirePlugin(ManifestIdentifier.fromString("io.manebot-media"));
        builder.instance(AudioPlugin.class, registration -> new AudioPlugin());
        builder.command("audio", AudioCommand::new);
        builder.command("mixer", MixerCommand::new);

        return builder.build();
    }

    @Override
    public void destruct(Plugin plugin) {
        throw new UnsupportedOperationException();
    }
}