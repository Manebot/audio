package com.github.manevolent.jbot.plugin.audio;

import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.PluginException;
import com.github.manevolent.jbot.plugin.PluginReference;
import com.github.manevolent.jbot.plugin.audio.command.AudioCommand;
import com.github.manevolent.jbot.plugin.audio.command.MixerCommand;
import com.github.manevolent.jbot.plugin.java.PluginEntry;

public class Entry implements PluginEntry {
    @Override
    public Plugin instantiate(Plugin.Builder builder) throws PluginException {
        return builder
                .instance(PluginReference.class, registration -> new AudioPlugin())
                .command("audio", AudioCommand::new)
                .command("mixer", MixerCommand::new)
                .build();
    }

    @Override
    public void destruct(Plugin plugin) {
        throw new UnsupportedOperationException();
    }
}