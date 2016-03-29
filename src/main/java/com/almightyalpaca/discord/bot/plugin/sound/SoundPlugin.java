package com.almightyalpaca.discord.bot.plugin.sound;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import com.almightyalpaca.discord.bot.system.command.AbstractCommand;
import com.almightyalpaca.discord.bot.system.command.annotation.Command;
import com.almightyalpaca.discord.bot.system.config.Config;
import com.almightyalpaca.discord.bot.system.config.exception.KeyNotFoundException;
import com.almightyalpaca.discord.bot.system.config.exception.WrongTypeException;
import com.almightyalpaca.discord.bot.system.events.CommandEvent;
import com.almightyalpaca.discord.bot.system.exception.PluginLoadingException;
import com.almightyalpaca.discord.bot.system.exception.PluginUnloadingException;
import com.almightyalpaca.discord.bot.system.plugins.Plugin;
import com.almightyalpaca.discord.bot.system.plugins.PluginInfo;
import com.almightyalpaca.discord.bot.system.util.StringUtils;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import net.dv8tion.jda.JDA;
import net.dv8tion.jda.MessageBuilder;
import net.dv8tion.jda.MessageBuilder.Formatting;
import net.dv8tion.jda.entities.VoiceChannel;
import net.dv8tion.jda.managers.AudioManager;
import net.dv8tion.jda.player.JDAPlayerConfig;
import net.dv8tion.jda.player.source.AudioInfo;
import net.dv8tion.jda.player.source.AudioSource;

public class SoundPlugin extends Plugin {

	public class AgainCommand extends AbstractCommand {

		public AgainCommand() {
			super("again", "Add the last song to the queue again", "");
		}

		@Command(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.player.add(SoundPlugin.this.player.getPreviousAudioSource());
				SoundPlugin.this.player.play();
			}
		}
	}

	public class JoinCommand extends AbstractCommand {

		public JoinCommand() {
			super("join", "Let's the bot join your channel", "");
		}

		@Command(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (event.getJDA().getAudioManager().getConnectedChannel() == null) {
				final VoiceChannel channel = event.getGuild().getVoiceStatusOfUser(event.getAuthor()).getChannel();
				if (channel != null) {
					event.getJDA().getAudioManager().openAudioConnection(channel);
				}
			}
		}
	}

	public class LeaveCommand extends AbstractCommand {

		public LeaveCommand() {
			super("leave", "Let's the bot join your channel", "");
		}

		@Command(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				event.getJDA().getAudioManager().closeAudioConnection();
			}
		}
	}

	public class ListCommand extends AbstractCommand {

		public ListCommand() {
			super("list", "List the queue", "(limit)");
		}

		@Command(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			this.onCommand(event, Integer.MAX_VALUE);
		}

		@Command(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event, int limit) {
			if (SoundPlugin.this.checkAccess(event)) {
				final MessageBuilder builder = new MessageBuilder();

				final AudioInfo info = SoundPlugin.this.player.getCurrentAudioSource().getInfo();

				builder.appendString("Playing:  ", Formatting.BOLD).appendString(info.getTitle()).newLine();
				builder.appendString("Time:	 ", Formatting.BOLD).appendString(info.getDuration().getTimestamp()).newLine();

				limit = Math.min(limit, SoundPlugin.this.player.getAudioQueue().size());
				final String digits = String.valueOf(String.valueOf(limit).length()); // Get the lenth of limit

				builder.newLine();
				builder.appendString("Queue:", Formatting.BOLD).newLine();

				for (int i = 0; i < SoundPlugin.this.player.getAudioQueue().size(); i++) {
					final AudioSource source = SoundPlugin.this.player.getAudioQueue().get(i);
					builder.appendString("[" + String.format("%0" + digits + "d", i + 1) + "] ", Formatting.BOLD).appendString(source.getInfo().getTitle()).newLine();
				}
				event.sendMessage(builder);
			}
		}
	}

	public class PauseCommand extends AbstractCommand {

		public PauseCommand() {
			super("pause", "Pause the music", "TODO");
		}

		@Command(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.player.pause();
			}
		}
	}

	public class PlayCommand extends AbstractCommand {

		public PlayCommand() {
			super("play", "Add something the queue", "[url] OR list [url]");
		}

		@Command(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.player.play();
			}
		}

		@Command(dm = false, guild = true, priority = 1, async = true)
		public void onCommand(final CommandEvent event, final String list, final URL url) {
			if (SoundPlugin.this.checkAccess(event)) {
				if (list.equalsIgnoreCase("list")) {
					final String listId = SoundPlugin.this.getPlaylistId(url.toString());
					if (list != null) {
						try {
							System.out.println(listId);
							final JSONObject response = Unirest.get("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=50&playlistId=" + listId + "&key="
									+ SoundPlugin.this.googleConfig.getString("API_KEY")).asJson().getBody().getObject();
							System.out.println(response.toString());
							for (int i = 0; i < response.getJSONArray("items").length(); i++) {
								final JSONObject item = response.getJSONArray("items").optJSONObject(i);
								final JSONObject snippet = item.getJSONObject("snippet");
								final JSONObject resourceId = snippet.getJSONObject("resourceId");
								SoundPlugin.this.player.add(new URL("https://www.youtube.com/watch?v=" + resourceId.getString("videoId")));
							}
							SoundPlugin.this.player.play();
						} catch (WrongTypeException | KeyNotFoundException | UnirestException | MalformedURLException | JSONException e) {
							e.printStackTrace();
						}
					}
				} else {
					this.onCommand(event);
				}
			}
		}

		@Command(dm = false, guild = true, priority = 1, async = true)
		public void onCommand(final CommandEvent event, final URL url) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.player.add(url);
				SoundPlugin.this.player.play();
			}
		}
	}

	public class PlayingCommand extends AbstractCommand {

		public PlayingCommand() {
			super("playing", "Show the current song", "");
		}

		@Command(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				final MessageBuilder builder = new MessageBuilder();
				final AudioInfo info = SoundPlugin.this.player.getCurrentAudioSource().getInfo();

				builder.appendString("Playing:  ", Formatting.BOLD).appendString(info.getTitle()).newLine();
				builder.appendString("Time:	  ", Formatting.BOLD).appendString(info.getDuration().getTimestamp());

				builder.send(event.getChannel());
			}
		}
	}

	public class SkipCommand extends AbstractCommand {

		public SkipCommand() {
			super("skip", "Skip current Song", "TODO");
		}

		@Command(dm = false, guild = true, async = true)
		public void onCommand(final CommandEvent event) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.player.skipToNext();
				SoundPlugin.this.player.play();
			}
		}
	}

	public class VolumeCommand extends AbstractCommand {

		public VolumeCommand() {
			super("volume", "Set the voume", "TODO");
		}

		@Command(dm = false, guild = true, priority = 1)
		public void onCommand(final CommandEvent event, final float f) {
			if (SoundPlugin.this.checkAccess(event)) {
				SoundPlugin.this.player.setVolume(Math.max(0, Math.min(1, f)));
			}
		}

		@Command(dm = false, guild = true, priority = 0)
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

	private static final PluginInfo	INFO	= new PluginInfo("com.almightyalpaca.discord.bot.plugin.sound", "1.0.0", "Almighty Alpaca", "Sound Plugin", "Music Bot and Soundboard");

	private SoundPlayer				player;

	private Config					googleConfig;

	public SoundPlugin() {
		super(SoundPlugin.INFO);
	}

	public boolean checkAccess(final CommandEvent event) {
		final VoiceChannel channel = this.getJDA().getAudioManager().getConnectedChannel();
		if (channel == null) {
			return true;
		}
		return channel.getUsers().contains(event.getAuthor());
	}

	public JDA getJDA() {
		return this.getBridge().getJDA();
	}

	private String getPlaylistId(final String string) {
		final Pattern pattern = Pattern.compile("(?:(?:\\?|&)list=)((?!videoseries)[a-zA-Z0-9_]*)");
		final Matcher matcher = pattern.matcher(string);
		return matcher.find() ? matcher.group().replace("&list=", "").replace("?list=", "") : null;
	}

	@Override
	public void load() throws PluginLoadingException {

		if (!AudioManager.AUDIO_SUPPORTED) {
			throw new PluginLoadingException();
		}
		
		this.googleConfig = this.getBridge().getSecureConfig("google");

		NativUtil.setFolder(new File(this.getPluginFolder(), "cache"));

		JDAPlayerConfig.setFFMPEG_COMMAND(NativUtil.getFFMPEGFile());
		JDAPlayerConfig.setYOUTUBE_DL_COMMAND(NativUtil.getYoutubeDLFile());

		this.player = new SoundPlayer();

		this.getJDA().getAudioManager().setSendingHandler(this.player);

		this.registerCommand(new JoinCommand());
		this.registerCommand(new LeaveCommand());
		this.registerCommand(new PlayCommand());
		this.registerCommand(new PauseCommand());
		this.registerCommand(new SkipCommand());
		this.registerCommand(new AgainCommand());
		this.registerCommand(new VolumeCommand());
		this.registerCommand(new PlayingCommand());
		this.registerCommand(new ListCommand());
	}

	@Override
	public void unload() throws PluginUnloadingException {
		this.getBridge().getJDA().getAudioManager().closeAudioConnection();
		if (this.getBridge().getJDA().getAudioManager().getSendingHandler() instanceof SoundPlayer) {
			this.getBridge().getJDA().getAudioManager().setSendingHandler(null);
		}
		this.player.shutdown();
	}

}
