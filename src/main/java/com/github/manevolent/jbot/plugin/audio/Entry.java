package com.github.manevolent.jbot.plugin.audio;

import com.github.manevolent.jbot.artifact.ManifestIdentifier;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.audio.command.AudioCommand;
import com.github.manevolent.jbot.plugin.audio.command.MixerCommand;
import com.github.manevolent.jbot.plugin.java.PluginEntry;

public class Entry implements PluginEntry {
    @Override
    public Plugin instantiate(Plugin.Builder builder) {
        return builder
                .require(ManifestIdentifier.fromString("com.github.manevolent:jbot-media"))
                .instance(AudioPlugin.class, registration -> new AudioPlugin())
                .command("audio", AudioCommand::new)
                .command("mixer", MixerCommand::new)
                .build();
    }

    @Override
    public void destruct(Plugin plugin) {
        throw new UnsupportedOperationException();
    }
}