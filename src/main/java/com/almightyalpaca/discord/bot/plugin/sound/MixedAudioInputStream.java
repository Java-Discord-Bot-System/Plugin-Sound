package com.almightyalpaca.discord.bot.plugin.sound;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.tritonus.share.sampled.TConversionTool;

public class MixedAudioInputStream extends AudioInputStream {

	private final List<AudioInputStream>	streams;
	private final AudioFormat				format;

	public MixedAudioInputStream(final AudioFormat format, final AudioInputStream stream) {
		this(format, Collections.singleton(stream));
	}

	public MixedAudioInputStream(final AudioFormat format, final Collection<AudioInputStream> streams) {
		super(new ByteArrayInputStream(new byte[0]), format, AudioSystem.NOT_SPECIFIED);

		this.format = format;
		this.streams = new ArrayList<>();

		for (final AudioInputStream stream : streams) {
			this.streams.add(AudioSystem.getAudioInputStream(format, stream));
		}

	}

	public boolean add(final AudioInputStream stream) {
		if (stream.getFormat().matches(this.format)) {
			this.streams.add(AudioSystem.getAudioInputStream(this.format, stream));
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void close() throws IOException {
		for (final AudioInputStream stream : this.streams) {
			stream.close();
		}
	}

	@Override
	public int read() throws IOException {
		int read = 0;
		final Iterator<AudioInputStream> iterator = this.streams.iterator();
		while (iterator.hasNext()) {
			final AudioInputStream stream = iterator.next();
			final int streamRead = stream.read();
			if (streamRead == -1) {
				iterator.remove();
			} else {
				read += streamRead;
			}
		}
		return (byte) (read & 0xFF);
	}

	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException {
		final int nChannels = this.getFormat().getChannels();
		final int nFrameSize = this.getFormat().getFrameSize();
		/*
		 * This value is in bytes. Note that it is the storage size. It may be four bytes for 24 bit samples.
		 */
		final int nSampleSize = nFrameSize / nChannels;
		final boolean bBigEndian = this.getFormat().isBigEndian();
		final AudioFormat.Encoding encoding = this.getFormat().getEncoding();

		final byte[] abBuffer = new byte[nFrameSize];
		final int[] anMixedSamples = new int[nChannels];
		for (int nFrameBoundry = 0; nFrameBoundry < len; nFrameBoundry += nFrameSize) {
			for (int i = 0; i < nChannels; i++) {
				anMixedSamples[i] = 0;
			}
			final Iterator<AudioInputStream> streamIterator = this.streams.iterator();
			while (streamIterator.hasNext()) {
				final AudioInputStream stream = streamIterator.next();

				final int nBytesRead = stream.read(abBuffer, 0, nFrameSize);

				/*
				 * TODO: we have to handle incomplete reads.
				 */
				if (nBytesRead == -1) {
					/*
					 * The end of the current stream has been signaled. We remove it from the list of streams.
					 */
					streamIterator.remove();
					continue;
				}
				for (int nChannel = 0; nChannel < nChannels; nChannel++) {
					final int nBufferOffset = nChannel * nSampleSize;
					int nSampleToAdd = 0;
					if (encoding.equals(AudioFormat.Encoding.PCM_SIGNED)) {
						switch (nSampleSize) {
							case 1:
								nSampleToAdd = abBuffer[nBufferOffset];
								break;
							case 2:
								nSampleToAdd = TConversionTool.bytesToInt16(abBuffer, nBufferOffset, bBigEndian);
								break;
							case 3:
								nSampleToAdd = TConversionTool.bytesToInt24(abBuffer, nBufferOffset, bBigEndian);
								break;
							case 4:
								nSampleToAdd = TConversionTool.bytesToInt32(abBuffer, nBufferOffset, bBigEndian);
								break;
						}
					}
					// TODO: pcm unsigned
					else if (encoding.equals(AudioFormat.Encoding.ALAW)) {
						nSampleToAdd = TConversionTool.alaw2linear(abBuffer[nBufferOffset]);
					} else if (encoding.equals(AudioFormat.Encoding.ULAW)) {
						nSampleToAdd = TConversionTool.ulaw2linear(abBuffer[nBufferOffset]);
					}
					anMixedSamples[nChannel] += nSampleToAdd;
				} // loop over channels
			} // loop over streams

			for (int nChannel = 0; nChannel < nChannels; nChannel++) {
				final int nBufferOffset = off + nFrameBoundry /* * nFrameSize */ + nChannel * nSampleSize;
				if (encoding.equals(AudioFormat.Encoding.PCM_SIGNED)) {
					switch (nSampleSize) {
						case 1:
							b[nBufferOffset] = (byte) anMixedSamples[nChannel];
							break;
						case 2:
							TConversionTool.intToBytes16(anMixedSamples[nChannel], b, nBufferOffset, bBigEndian);
							break;
						case 3:
							TConversionTool.intToBytes24(anMixedSamples[nChannel], b, nBufferOffset, bBigEndian);
							break;
						case 4:
							TConversionTool.intToBytes32(anMixedSamples[nChannel], b, nBufferOffset, bBigEndian);
							break;
					}
				}
				// TODO: pcm unsigned
				else if (encoding.equals(AudioFormat.Encoding.ALAW)) {
					b[nBufferOffset] = TConversionTool.linear2alaw((short) anMixedSamples[nChannel]);
				} else if (encoding.equals(AudioFormat.Encoding.ULAW)) {
					b[nBufferOffset] = TConversionTool.linear2ulaw(anMixedSamples[nChannel]);
				}
			} // (final) loop over channels
		} // loop over frames

		// TODO: return a useful value
		return len;
	}

	public void remove(final AudioInputStream stream) {
		this.streams.remove(stream);
	}

	@Override
	public long skip(final long n) throws IOException {
		for (final AudioInputStream stream : this.streams) {
			stream.skip(n);
		}
		return n;
	}

//
//	@Override
//	public boolean markSupported() {
//		for (AudioInputStream stream : streams) {
//			if (!stream.markSupported()) {
//				return false;
//			}
//		}
//		return true;
//	}
//
//	@Override
//	public void mark(int readlimit) {
//		for (AudioInputStream stream : streams) {
//			stream.mark(readlimit);
//		}
//	}
//
//	@Override
//	public void reset() throws IOException {
//		for (AudioInputStream stream : streams) {
//			stream.reset();
//		}
//	}

}
