package net.dv8tion.jda.player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JDAPlayerConfig {

	static {
		JDAPlayerConfig.initYOUTUBE_DL_LAUNCH_ARGS();
		JDAPlayerConfig.initFFMPEG_LAUNCH_ARGS();
	}

	private static List<String>	YOUTUBE_DL_LAUNCH_ARGS;
	private static List<String>	FFMPEG_LAUNCH_ARGS;

	private static String		PYTHON_COMMAND		= "python";
	private static String		YOUTUBE_DL_COMMAND	= "./youtube-dl";
	private static String		FFMPEG_COMMAND		= "ffmpeg";

	public static List<String> getFFMPEG_LAUNCH_ARGS() {
		return JDAPlayerConfig.FFMPEG_LAUNCH_ARGS;
	}

	public static List<String> getYOUTUBE_DL_LAUNCH_ARGS() {
		return JDAPlayerConfig.YOUTUBE_DL_LAUNCH_ARGS;
	}

	private static void initFFMPEG_LAUNCH_ARGS() {
		JDAPlayerConfig.FFMPEG_LAUNCH_ARGS = Collections.unmodifiableList(Arrays.asList(
				JDAPlayerConfig.FFMPEG_COMMAND, // Program launch
				"-i", "-",                      // Input file, specifies to read from STDin (pipe)
				"-f", "s16be",                  // Format. PCM, signed, 16bit, Big Endian
				"-ac", "2",                     // Channels. Specify 2 for stereo audio.
				"-ar", "48000",                 // Rate. Opus requires an audio rate of 48000hz
				"-map", "a",                    // Makes sure to only output audio, even if the specified format supports other streams
				"-"                             // Used to specify STDout as the output location (pipe)
		));
	}

	private static void initYOUTUBE_DL_LAUNCH_ARGS() {
		JDAPlayerConfig.YOUTUBE_DL_LAUNCH_ARGS = Collections.unmodifiableList(Arrays.asList(
				JDAPlayerConfig.PYTHON_COMMAND,     // Launch python executor
				JDAPlayerConfig.YOUTUBE_DL_COMMAND, // youtube-dl program file
				"-q",                               // quiet. No standard out.
				"-f", "bestaudio/best",             // Format to download. Attempts best audio-only, followed by best video/audio combo
				"--no-playlist",                    // If the provided link is part of a Playlist, only grabs the video, not playlist too.
				"-o", "-"                           // Output, output to STDout
		));
	}

	public static void setFFMPEG_COMMAND(final String command) {
		JDAPlayerConfig.FFMPEG_COMMAND = command;
		JDAPlayerConfig.initFFMPEG_LAUNCH_ARGS();
    }

	public static void setPYTHON_COMMAND(final String command) {
		JDAPlayerConfig.PYTHON_COMMAND = command;
		JDAPlayerConfig.initYOUTUBE_DL_LAUNCH_ARGS();
    }

	public static void setYOUTUBE_DL_COMMAND(final String command) {
		JDAPlayerConfig.YOUTUBE_DL_COMMAND = command;
		JDAPlayerConfig.initYOUTUBE_DL_LAUNCH_ARGS();
    }

}
