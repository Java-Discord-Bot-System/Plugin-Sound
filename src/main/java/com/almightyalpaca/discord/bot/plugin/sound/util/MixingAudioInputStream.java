/*
 * MixingAudioInputStream.java This file is part of jsresources.org This code follows an idea of Paul Sorenson.
 */

/*
 * Copyright (c) 1999 - 2001 by Matthias Pfisterer All rights reserved. Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met: - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. - Redistributions in binary form must reproduce
 * the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. THIS SOFTWARE IS PROVIDED BY THE
 * COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.almightyalpaca.discord.bot.plugin.sound.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/*
 * This is a class of Tritonus. It's not one of the best ideas to use it here. However, we really don't want to reimplement its functionality here. You need to have tritonus_share.jar in the
 * classpath. Get it from http://www.tritonus.org .
 */
import org.tritonus.share.sampled.TConversionTool;

/**
 * Mixing of multiple AudioInputStreams to one AudioInputStream. This class takes a collection of AudioInputStreams and mixes them together. Being a subclass of AudioInputStream itself, reading from
 * instances of this class behaves as if the mixdown result of the input streams is read.
 * 
 * @author Matthias Pfisterer
 */
public class MixingAudioInputStream extends AudioInputStream {

	private final List<AudioInputStream> m_audioInputStreamList;

	public MixingAudioInputStream(final AudioFormat audioFormat, final Collection<AudioInputStream> audioInputStreams) {
		super(new ByteArrayInputStream(new byte[0]), audioFormat, AudioSystem.NOT_SPECIFIED);

		this.m_audioInputStreamList = new ArrayList<AudioInputStream>(audioInputStreams);

	}

	/**
	 * The minimum of available() of all input stream is calculated and returned.
	 */
	@Override
	public int available() throws IOException {
		int nAvailable = 0;
		final Iterator<AudioInputStream> streamIterator = this.m_audioInputStreamList.iterator();
		while (streamIterator.hasNext()) {
			final AudioInputStream stream = streamIterator.next();
			nAvailable = Math.min(nAvailable, stream.available());
		}
		return nAvailable;
	}

	@Override
	public void close() throws IOException {
		// TODO: should we close all streams in the list?
	}

	/**
	 * The maximum of the frame length of the input stream is calculated and returned. If at least one of the input streams has length <code>AudioInputStream.NOT_SPECIFIED</code>, this value is
	 * returned.
	 */
	@Override
	public long getFrameLength() {
		long lLengthInFrames = 0;
		final Iterator<AudioInputStream> streamIterator = this.m_audioInputStreamList.iterator();
		while (streamIterator.hasNext()) {
			final AudioInputStream stream = streamIterator.next();
			final long lLength = stream.getFrameLength();
			if (lLength == AudioSystem.NOT_SPECIFIED) {
				return AudioSystem.NOT_SPECIFIED;
			} else {
				lLengthInFrames = Math.max(lLengthInFrames, lLength);
			}
		}
		return lLengthInFrames;
	}

	/**
	 * Calls mark() on all input streams.
	 */
	@Override
	public void mark(final int nReadLimit) {
		final Iterator<AudioInputStream> streamIterator = this.m_audioInputStreamList.iterator();
		while (streamIterator.hasNext()) {
			final AudioInputStream stream = streamIterator.next();
			stream.mark(nReadLimit);
		}
	}

	/**
	 * returns true if all input stream return true for markSupported().
	 */
	@Override
	public boolean markSupported() {
		final Iterator<AudioInputStream> streamIterator = this.m_audioInputStreamList.iterator();
		while (streamIterator.hasNext()) {
			final AudioInputStream stream = streamIterator.next();
			if (!stream.markSupported()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int read() throws IOException {

		int nSample = 0;
		final Iterator<AudioInputStream> streamIterator = this.m_audioInputStreamList.iterator();
		while (streamIterator.hasNext()) {
			final AudioInputStream stream = streamIterator.next();
			final int nByte = stream.read();
			if (nByte == -1) {
				/*
				 * The end of this stream has been signaled. We remove the stream from our list.
				 */
				streamIterator.remove();
				continue;
			} else {
				/*
				 * what about signed/unsigned?
				 */
				nSample += nByte;
			}
		}

		return (byte) (nSample & 0xFF);
	}

	@Override
	public int read(final byte[] abData, final int nOffset, final int nLength) throws IOException {

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
		for (int nFrameBoundry = 0; nFrameBoundry < nLength; nFrameBoundry += nFrameSize) {
			for (int i = 0; i < nChannels; i++) {
				anMixedSamples[i] = 0;
			}
			final Iterator<AudioInputStream> streamIterator = this.m_audioInputStreamList.iterator();
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
				final int nBufferOffset = nOffset + nFrameBoundry /* * nFrameSize */ + nChannel * nSampleSize;
				if (encoding.equals(AudioFormat.Encoding.PCM_SIGNED)) {
					switch (nSampleSize) {
						case 1:
							abData[nBufferOffset] = (byte) anMixedSamples[nChannel];
							break;
						case 2:
							TConversionTool.intToBytes16(anMixedSamples[nChannel], abData, nBufferOffset, bBigEndian);
							break;
						case 3:
							TConversionTool.intToBytes24(anMixedSamples[nChannel], abData, nBufferOffset, bBigEndian);
							break;
						case 4:
							TConversionTool.intToBytes32(anMixedSamples[nChannel], abData, nBufferOffset, bBigEndian);
							break;
					}
				}
				// TODO: pcm unsigned
				else if (encoding.equals(AudioFormat.Encoding.ALAW)) {
					abData[nBufferOffset] = TConversionTool.linear2alaw((short) anMixedSamples[nChannel]);
				} else if (encoding.equals(AudioFormat.Encoding.ULAW)) {
					abData[nBufferOffset] = TConversionTool.linear2ulaw(anMixedSamples[nChannel]);
				}
			} // (final) loop over channels
		} // loop over frames

		// TODO: return a useful value
		return nLength;
	}

	/**
	 * Calls reset() on all input streams.
	 */
	@Override
	public void reset() throws IOException {
		final Iterator<AudioInputStream> streamIterator = this.m_audioInputStreamList.iterator();
		while (streamIterator.hasNext()) {
			final AudioInputStream stream = streamIterator.next();
			stream.reset();
		}
	}

	/**
	 * calls skip() on all input streams. There is no way to assure that the number of bytes really skipped is the same for all input streams. Due to that, this method always returns the passed value.
	 * In other words: the return value is useless (better ideas appreciated).
	 */
	@Override
	public long skip(final long lLength) throws IOException {
		final Iterator<AudioInputStream> streamIterator = this.m_audioInputStreamList.iterator();
		while (streamIterator.hasNext()) {
			final AudioInputStream stream = streamIterator.next();
			stream.skip(lLength);
		}
		return lLength;
	}

}
