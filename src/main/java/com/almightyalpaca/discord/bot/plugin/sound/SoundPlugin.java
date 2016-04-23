package com.almightyalpaca.discord.bot.plugin.sound;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import com.almightyalpaca.discord.bot.system.command.Command;
import com.almightyalpaca.discord.bot.system.command.CommandHandler;
import com.almightyalpaca.discord.bot.system.config.Config;
import com.almightyalpaca.discord.bot.system.config.exception.KeyNotFoundException;
import com.almightyalpaca.discord.bot.system.config.exception.WrongTypeException;
import com.almightyalpaca.discord.bot.system.events.commands.CommandEvent;
import com.almightyalpaca.discord.bot.system.exception.PluginLoadingException;
import com.almightyalpaca.discord.bot.system.exception.PluginUnloadingException;
import com.almightyalpaca.discord.bot.system.plugins.Plugin;
import com.almightyalpaca.discord.bot.system.plugins.PluginInfo;
import com.almightyalpaca.discord.bot.system.util.MathUtil;
import com.almightyalpaca.discord.bot.system.util.StringUtils;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import net.dv8tion.jda.MessageBuilder;
import net.dv8tion.jda.MessageBuilder.Formatting;
import net.dv8tion.jda.audio.AudioSendHandler;
import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.VoiceChannel;
import net.dv8tion.jda.entities.impl.JDAImpl;
import net.dv8tion.jda.managers.AudioManager;
import net.dv8tion.jda.player.JDAPlayerConfig;
import net.dv8tion.jda.player.source.AudioInfo;
import net.dv8tion.jda.player.source.AudioSource;
import net.dv8tion.jda.player.source.AudioTimestamp;

public class SoundPlugin extends Plugin {

	public class AgainCommand extends Command {

		public AgainCommand() {
			super("again", "Add the last song to the queue again", "");
		}

		@CommandHandler(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.getPlayer(event).add(SoundPlugin.this.getPlayer(event).getPreviousAudioSource());
				SoundPlugin.this.getPlayer(event).play();
			}
		}
	}

	public class JoinCommand extends Command {

		public JoinCommand() {
			super("join", "Let's the bot join your channel", "");
		}

		@CommandHandler(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (event.getJDA().getAudioManager(event.getGuild()).getConnectedChannel() == null) {
				final VoiceChannel channel = event.getGuild().getVoiceStatusOfUser(event.getAuthor()).getChannel();
				if (channel != null) {
					event.getJDA().getAudioManager(event.getGuild()).openAudioConnection(channel);
				}
			}
		}
	}

	public class LeaveCommand extends Command {

		public LeaveCommand() {
			super("leave", "Let's the bot join your channel", "");
		}

		@CommandHandler(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				event.getJDA().getAudioManager(event.getGuild()).closeAudioConnection();
			}
		}
	}

	public class ListCommand extends Command {

		public ListCommand() {
			super("list", "List the queue", "(limit)");
		}

		@CommandHandler(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			this.onCommand(event, Integer.MAX_VALUE);
		}

		@CommandHandler(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event, int limit) {
			final MessageBuilder builder = new MessageBuilder();

			final AudioInfo info = SoundPlugin.this.getPlayer(event).getCurrentAudioSource().getInfo();

			builder.appendString("Playing:  ", Formatting.BOLD).appendString(info.getTitle()).newLine();
			builder.appendString("Time:	 ", Formatting.BOLD).appendString(info.getDuration().getTimestamp()).newLine();

			limit = Math.min(limit, SoundPlugin.this.getPlayer(event).getAudioQueue().size());
			final String digits = String.valueOf(String.valueOf(limit).length()); // Get the lenth of limit

			builder.newLine();
			builder.appendString("Queue:", Formatting.BOLD).newLine();

			for (int i = 0; i < limit; i++) {
				final AudioSource source = SoundPlugin.this.getPlayer(event).getAudioQueue().get(i);
				builder.appendString("[" + String.format("%0" + digits + "d", i + 1) + "] ", Formatting.BOLD).appendString(source.getInfo().getTitle()).newLine();
			}
			event.sendMessage(builder);
		}
	}

	public class PauseCommand extends Command {

		public PauseCommand() {
			super("pause", "Pause the music", "TODO");
		}

		@CommandHandler(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.getPlayer(event).pause();
			}
		}
	}

	public class PlayCommand extends Command {

		public PlayCommand() {
			super("play", "Add something the queue", "[url] OR list [url]");
		}

		@CommandHandler(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.getPlayer(event).play();
			}
		}

		@CommandHandler(dm = false, guild = true, priority = 1, async = true)
		public void onCommand(final CommandEvent event, final String list, final URL url) {
			if (SoundPlugin.this.checkAccess(event)) {
				if (list.equalsIgnoreCase("list")) {
					final String listId = SoundPlugin.this.getPlaylistId(url.toString());
					if (list != null) {
						try {
							final JSONObject response = Unirest
								.get("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=50&playlistId=" + listId + "&key=" + SoundPlugin.this.googleConfig.getString("key"))
								.asJson().getBody().getObject();
							for (int i = 0; i < response.getJSONArray("items").length(); i++) {
								final JSONObject item = response.getJSONArray("items").optJSONObject(i);
								final JSONObject snippet = item.getJSONObject("snippet");
								final JSONObject resourceId = snippet.getJSONObject("resourceId");
								SoundPlugin.this.getPlayer(event).add(new URL("https://www.youtube.com/watch?v=" + resourceId.getString("videoId")));
							}
							SoundPlugin.this.getPlayer(event).play();
						} catch (WrongTypeException | KeyNotFoundException | UnirestException | MalformedURLException | JSONException e) {
							e.printStackTrace();
						}
					}
				} else {
					this.onCommand(event);
				}
			}
		}

		@CommandHandler(dm = false, guild = true, priority = 1, async = true)
		public void onCommand(final CommandEvent event, final URL url) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.getPlayer(event).add(url);
				SoundPlugin.this.getPlayer(event).play();
			}
		}
	}

	public class PlayingCommand extends Command {

		public PlayingCommand() {
			super("playing", "Show the current song", "");
		}

		@CommandHandler(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {

			final MessageBuilder builder = new MessageBuilder();

			final AudioInfo info = SoundPlugin.this.getPlayer(event).getCurrentAudioSource().getInfo();
			final AudioTimestamp timestamp = SoundPlugin.this.getPlayer(event).getCurrentTimestamp();

			final int totalWidth = 25;

			final float volume = SoundPlugin.this.getPlayer(event).getVolume();
			final boolean playing = SoundPlugin.this.getPlayer(event).isPlaying();
			final int currentTime = timestamp.getTotalSeconds();
			final int totalTime = info.getDuration().getTotalSeconds();
			final int before = currentTime * totalWidth / totalTime;
			final int after = totalWidth - before - 1;

			builder.appendString(info.getTitle(), Formatting.BOLD).newLine().newLine();

			// arrow_forward or pause_button
			if (playing) {
				builder.appendString("\u23F8");
			} else {
				builder.appendString("\u25B6");
			}

			builder.appendString("  ");

			// current time
			builder.appendString(timestamp.getTimestamp());

			builder.appendString(" ");

			// line
			for (int i = 0; i < before; i++) {
				builder.appendString("\u25AC");
			}
			builder.appendString("\uD83D\uDD18"); // current position
			for (int i = 0; i < after; i++) {
				builder.appendString("\u25AC");
			}

			builder.appendString(" ");

			// total time
			builder.appendString(info.getDuration().getTimestamp());

			builder.appendString("  ");

			//Speaker
			if (volume == 0) {
				builder.appendString("\uD83D\uDD07");
			} else if (volume < 0.25) {
				builder.appendString("\uD83D\uDD08");
			} else if (volume < 0.5) {
				builder.appendString("\uD83D\uDD09");
			} else {
				builder.appendString("\uD83D\uDD0A");
			}

			builder.send(event.getChannel());
		}
	}

	public class SkipCommand extends Command {

		public SkipCommand() {
			super("skip", "Skip current Song", "TODO");
		}

		@CommandHandler(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.getPlayer(event).skipToNext();
				SoundPlugin.this.getPlayer(event).play();
			}
		}
	}

	public class StopCommand extends Command {

		public StopCommand() {
			super("stop", "Stops thge player, clearing the queue and leving the channel", "TODO");
		}

		@CommandHandler(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				if (event.getGuild().getAudioManager().getSendingHandler() instanceof SoundPlayer) {
					((SoundPlayer) event.getGuild().getAudioManager().getSendingHandler()).shutdown();
				}
				event.getGuild().getAudioManager().setSendingHandler(null);
				event.getGuild().getAudioManager().closeAudioConnection();
			}
		}
	}

	public class VolumeCommand extends Command {

		public VolumeCommand() {
			super("volume", "Set the voume", "TODO");
		}

		@CommandHandler(dm = false, guild = true, priority = 1)
		public void onCommand(final CommandEvent event, final float f) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.getPlayer(event).setVolume(MathUtil.limit(0, f, 1));
			}
		}

		@CommandHandler(dm = false, guild = true, priority = 0)
		public void onCommand(final CommandEvent event, final String s) {
			if (s.endsWith("%")) {
				final String percentage = StringUtils.replaceLast(s, "%", "");
				try {
					final float f = Float.parseFloat(percentage) / 100;
					this.onCommand(event, f);
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static final PluginInfo INFO = new PluginInfo("com.almightyalpaca.discord.bot.plugin.sound", "1.0.0", "Almighty Alpaca", "Sound Plugin", "Music Bot and Soundboard");

	private Config googleConfig;

	public SoundPlugin() {
		super(SoundPlugin.INFO);
	}

	public boolean checkAccess(final CommandEvent event) {
		final VoiceChannel channel = this.getJDA().getAudioManager(event.getGuild()).getConnectedChannel();
		if (channel == null) {
			return true;
		}
		return channel.getUsers().contains(event.getAuthor());
	}

	public SoundPlayer getPlayer(final CommandEvent event) {
		return this.getPlayer(event.getGuild());
	}

	public SoundPlayer getPlayer(final Guild guild) {
		final AudioSendHandler handler = guild.getAudioManager().getSendingHandler();
		SoundPlayer player;
		if (handler instanceof SoundPlayer) {
			player = (SoundPlayer) handler;
		} else {
			player = new SoundPlayer();
			guild.getAudioManager().setSendingHandler(player);
		}
		return player;

	}

	private String getPlaylistId(final String string) {
		final Pattern pattern = Pattern.compile("(?:(?:\\?|&)list=)((?!videoseries)[a-zA-Z0-9_]*)");
		final Matcher matcher = pattern.matcher(string);
		return matcher.find() ? matcher.group().replace("&list=", "").replace("?list=", "") : null;
	}

	@Override
	public void load() throws PluginLoadingException {

		if (!this.getJDA().isAudioEnabled()) {
			throw new PluginLoadingException();
		}

		this.googleConfig = this.getSharedConfig("google");

		if (this.googleConfig.getString("key", "Your Key") == "Your Key") {
			throw new PluginLoadingException("Pls add your google api key to the config");
		}

		NativUtil.setFolder(new File(this.getPluginFolder(), "cache"));

		JDAPlayerConfig.setFFMPEG_COMMAND(NativUtil.getFFMPEGFile());
		JDAPlayerConfig.setYOUTUBE_DL_COMMAND(NativUtil.getYoutubeDLFile());

		this.registerCommand(new JoinCommand());
		this.registerCommand(new LeaveCommand());
		this.registerCommand(new PlayCommand());
		this.registerCommand(new PauseCommand());
		this.registerCommand(new SkipCommand());
		this.registerCommand(new AgainCommand());
		this.registerCommand(new VolumeCommand());
		this.registerCommand(new PlayingCommand());
		this.registerCommand(new ListCommand());
		this.registerCommand(new StopCommand());
	}

	@Override
	public void unload() throws PluginUnloadingException {
		for (final AudioManager manager : ((JDAImpl) this.getJDA()).getAudioManagersMap().values()) {
			if (manager.getSendingHandler() instanceof SoundPlayer) {
				((SoundPlayer) manager.getSendingHandler()).shutdown();
			}
			manager.setSendingHandler(null);
			manager.closeAudioConnection();
		}
	}

}
