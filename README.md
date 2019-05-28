# Manebot Audio

This is the official audio plugin for my multi-platform chatbot, Manebot.  This plugin provides audio support for plugins such as Teamspeak and Discord, and acts as a foundation for the Music plugin and its associated plugins.

This is simply an API, and doesn't play music tracks on its own, although it's all a platform needs to support those features.  To see the dependency that does, check out `music`: https://github.com/Manevolent/manebot-music

## Concepts

<img src="https://github.com/Manevolent/manebot-audio/raw/master/pipeline.png">

* **Conversations** map to **AudioChannels**, which in turn are directly associated to a **Mixer**.
* **Mixers** read from multiple blocking **MixerChannels**, "summing" (+) their outputs, filter the mixed audio, and finally write to multiple blocking **MixerSinks** in a fan-out fashion.
* **MixerChannel** is the foundational interface that provides essential audio input.  Examples of MixerChannels are: a music track (stream from a file or URI), text-to-speech, or sound effects.
* **MixerSink** is the foundational interface that provides essential audio output.  Examples of MixerSinks are: output to a Teamspeak or Discord channel, a file to save to, or input to another Mixer (via a MixerChannel bridge)
* **Filters** process audio after it's been summed in a *Mixer*, like an EQ, compressor, limiter, or clip filter.  You can write your own filters, or use the baisc ones provided in the source code.  Every Mixer has its own set of filters, but user-default filters can be relied on.
* **AudioPlayers** extend *MixerChannels* and can support *transition fade-in/fade-out*, resampling, and most important, audio file *InputStreams* via FFmpeg4j: https://github.com/Manevolent/ffmpeg4j (so, every audio format FFmpeg can suport)

## Getting started

Note: your plugin should have a *provided dependency* on *io.manebot.plugin:audio*.

**Audio** is an *instance* class that is provided by the Audio plugin itself.  You can get its instance by getting `getInstance()`.  In my example below, I call `getPlugin()` (offered in several places in Manebot plugin entry):

```
Plugin audioPlugin = getPlugin(ManifestIdentifier.fromString("io.manebot.plugin:audio"));
if (audioPlugin == null)
    throw new IllegalStateException("audio");
    
Audio audio = audioPlugin.getInstance(Audio.class);
```

You can do any work you need to do with the audio subsystem with the `Audio` class.

## Examples

### Offering audio support from your platform plugin

See working examples of how it was done in the **Discord** or **Teamspeak3** plugins:
* https://github.com/Manevolent/manebot-discord/blob/master/src/main/java/io/manebot/plugin/discord/Entry.java
* https://github.com/Manevolent/manebot-ts3/blob/master/src/main/java/io/manebot/plugin/ts3/Entry.java

First, extend and construct an `AudioConnection` class:

```
final DiscordPlatformConnection platformConnection = new DiscordPlatformConnection(
        myPlatform,
        myPlugin,
        audio
);
```

It's important to have just one *connection* to the audio system, instead of managing multiple for different chats, MUCs, etc. -- this is not possible since `AudioConnection`s are mapped to `Platform`s in a one-to-one relationship.

Then, register your connection instance above into the audio system; you'll get an `AudioRegistration` unique to your platform to play with:

```
AudioRegistration registration = audio.createRegistration(
        platformBuilder.getPlatform(),
        consumer -> consumer.setConnection(platformConnection.getAudioConnection())
);
```

It's up to you for your custom `AudioConnection` implementor to provide `AudioChannel`s to the audio plugin when it needs them (they can return null `AudioChannel`s, too if one doesn't exist):

Remember that `Chat`s are the network representation of a multi or single-user conversation, and are associated one-to-one with `Conversation`s in Manebot.

```
private class DiscordAudioConnection extends AbstractAudioConnection {
    private DiscordAudioConnection(Audio audio) {
        super(audio);
    }

    @Override
    public AudioChannel getChannel(Chat chat) {
        if (chat instanceof DiscordGuildChannel) {
            DiscordGuildConnection connection = ((DiscordGuildChannel) chat).getGuildConnection();

            if (connection == null || !connection.isRegistered()) {
                return null;
            }

            return connection.getAudioChannel();
        } else return null;
    }

    @Override
    public boolean isConnected() {
        return super.isConnected() && DiscordPlatformConnection.this.isConnected();
    }
}
```
