package com.almightyalpaca.discord.bot.plugin.sound;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

import be.tarsos.dsp.util.FFMPEGDownloader;

public class NativUtil {

	private static File				folder;

	private static FFMPEGDownloader	downloader;

	public static String getFFMPEGFile() {
		if (NativUtil.downloader == null) {
			NativUtil.downloader = new FFMPEGDownloader(NativUtil.folder.getAbsolutePath());
		}
		return NativUtil.downloader.ffmpegBinary();
	}

	public static String getYoutubeDLFile() {

		final File youtubedl = new File(NativUtil.folder, "youtube-dl.py");

		if (!youtubedl.exists()) {
			try {
				youtubedl.getParentFile().mkdirs();
				youtubedl.createNewFile();

				IOUtils.copy(NativUtil.class.getResourceAsStream("/youtube_dl.py"), new FileOutputStream(youtubedl));
			} catch (IOException | SecurityException | IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return youtubedl.getAbsolutePath();
	}

	public static void setFolder(final File folder) {
		NativUtil.folder = folder;
	}
}
