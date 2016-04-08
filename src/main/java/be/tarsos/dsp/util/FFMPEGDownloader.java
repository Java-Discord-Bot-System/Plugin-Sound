package be.tarsos.dsp.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.logging.Logger;

/**
 * Downloads a static ffmpeg binary for several platforms: Windows x64 and x32 Max OS X x64 Linux x32 and x64 The code tries to determine the correct platform and downloads it to
 * the temporary directory <code>System.getProperty("java.io.tmpdir")</code>. After downloading it makes the binary executable. The location of the downloaded binary is returned by
 * <code>ffmpegBinary();</code>
 * 
 * @author Joren Six
 */
public class FFMPEGDownloader {

	private static String url = "http://0110.be/releases/TarsosDSP/TarsosDSP-static-ffmpeg/";

	private final static Logger LOG = Logger.getLogger(FFMPEGDownloader.class.getName());

	private final String ffmpegBinary;

	public FFMPEGDownloader() {
		this(System.getProperty("java.io.tmpdir"));
	}

	public FFMPEGDownloader(final String folder) {
		final String filename = this.operatingSystemName() + "_" + this.processorArchitecture() + "_ffmpeg" + this.suffix();
		FFMPEGDownloader.url = FFMPEGDownloader.url + filename;

		final String saveTo = new File(folder, filename).getAbsolutePath();

		if (new File(saveTo).exists()) {
			FFMPEGDownloader.LOG.info("Found an already download ffmpeg static binary: " + saveTo);
			this.ffmpegBinary = saveTo;
		} else {
			FFMPEGDownloader.LOG.info("Started downloading an ffmpeg static binary from  " + FFMPEGDownloader.url);
			this.downloadExecutable(saveTo);

			if (new File(saveTo).exists()) {
				FFMPEGDownloader.LOG.info("Downloaded an ffmpeg static binary. Stored at: " + saveTo);
				// make it executable
				new File(saveTo).setExecutable(true);
				this.ffmpegBinary = saveTo;
			} else {
				// Unable to download or unknown architecture
				FFMPEGDownloader.LOG.warning("Unable to find or download an ffmpeg static binary.  " + filename);
				this.ffmpegBinary = null;
			}
		}
	}

	public static void main(final String... strings) {
		new FFMPEGDownloader();
	}

	private void downloadExecutable(final String saveTo) {
		try {
			final File file = new File(saveTo);
			if (file.exists()) {
				file.delete();
			}
			file.getParentFile().mkdirs();
			file.createNewFile();
			final URL website = new URL(FFMPEGDownloader.url);
			final ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			final FileOutputStream fos = new FileOutputStream(file);
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			fos.close();
		} catch (final MalformedURLException e) {
			e.printStackTrace();
		} catch (final IOException e) {

			e.printStackTrace();
		}
	}

	public String ffmpegBinary() {
		return this.ffmpegBinary;
	}

	private String operatingSystemName() {
		String name;
		final String operatingSystem = System.getProperty("os.name").toLowerCase();
		if (operatingSystem.indexOf("indows") > 0) {
			name = "windows";
		} else if (operatingSystem.indexOf("nux") >= 0) {
			name = "linux";
		} else if (operatingSystem.indexOf("mac") >= 0) {
			name = "mac_os_x";
		} else {
			name = null;
		}
		return name;
	}

	private String processorArchitecture() {
		boolean is64bit = false;
		if (System.getProperty("os.name").contains("Windows")) {
			is64bit = System.getenv("ProgramFiles(x86)") != null;
		} else {
			is64bit = System.getProperty("os.arch").indexOf("64") != -1;
		}
		if (is64bit) {
			return "64_bits";
		} else {
			return "32_bits";
		}
	}

	private String suffix() {
		String suffix = "";
		if (System.getProperty("os.name").contains("Windows")) {
			suffix = ".exe";
		}
		return suffix;
	}
}
