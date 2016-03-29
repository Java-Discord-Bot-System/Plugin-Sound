/**
 *     Copyright 2016 Austin Keener
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.player.source;

import org.json.JSONException;
import org.json.JSONObject;

import net.dv8tion.jda.player.JDAPlayerConfig;
import sun.misc.IOUtils;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;

public class RemoteSource implements AudioSource
{
    private final String url;
    private final List<String> ytdlLaunchArgsF;
    private final List<String> ffmpegLaunchArgsF;
    private AudioInfo audioInfo;

    public RemoteSource(String url)
    {
        this(url, null, null);
    }

    public RemoteSource(String url, List<String> ytdlLaunchArgs, List<String> ffmpegLaunchArgs)
    {
        if (url == null || url.isEmpty())
            throw new NullPointerException("String url provided to RemoteSource was null or empty.");
        this.url = url;
        this.ytdlLaunchArgsF = ytdlLaunchArgs;
        this.ffmpegLaunchArgsF = ffmpegLaunchArgs;
    }

    public String getSource()
    {
        return url;
    }

    @Override
    public AudioInfo getInfo()
    {
        if (audioInfo != null)
            return audioInfo;

        List<String> infoArgs = new LinkedList<>();
        if (ytdlLaunchArgsF != null)
        {
            infoArgs.addAll(ytdlLaunchArgsF);
            if (!infoArgs.contains("-q"))
                infoArgs.add("-q");
        }
        else
            infoArgs.addAll(JDAPlayerConfig.getYOUTUBE_DL_LAUNCH_ARGS());

        infoArgs.add("--ignore-errors");    //Ignore errors, obviously
        infoArgs.add("-j");                 //Dumps the json about the file into STDout
        infoArgs.add("--skip-download");    //Doesn't actually download the file.
        infoArgs.add(url);                  //specifies the URL to download.

        audioInfo = new AudioInfo();
        try
        {
            Process infoProcess = new ProcessBuilder().command(infoArgs).start();
            byte[] infoData = IOUtils.readFully(infoProcess.getErrorStream(), -1, false);   //YT-DL outputs to STDerr
            if (infoData == null || infoData.length == 0)
                throw new NullPointerException("The Youtube-DL process resulted in a null or zero-length INFO!");

            String infoString = new String(infoData);
            if (infoString.startsWith("ERROR"))
            {
                audioInfo.error = infoString;
            }
            else
            {
                JSONObject info = new JSONObject(infoString);

                audioInfo.jsonInfo = info;
                audioInfo.title = !info.optString("title", "").isEmpty()
                        ? info.getString("title")
                        : !info.optString("fulltitle", "").isEmpty()
                        ? info.getString("fulltitle")
                        : null;
                audioInfo.origin = !info.optString("webpage_url", "").isEmpty()
                        ? info.getString("webpage_url")
                        : url;
                audioInfo.id = !info.optString("id", "").isEmpty()
                        ? info.getString("id")
                        : null;
                audioInfo.encoding = !info.optString("acodec", "").isEmpty()
                        ? info.getString("acodec")
                        : !info.optString("ext", "").isEmpty()
                        ? info.getString("ext")
                        : null;
                audioInfo.description = !info.optString("description", "").isEmpty()
                        ? info.getString("description")
                        : null;
                audioInfo.extractor = !info.optString("extractor", "").isEmpty()
                        ? info.getString("extractor")
                        : !info.optString("extractor_key").isEmpty()
                        ? info.getString("extractor_key")
                        : null;
                audioInfo.thumbnail = !info.optString("thumbnail", "").isEmpty()
                        ? info.getString("thumbnail")
                        : null;
                audioInfo.duration = info.optInt("duration", -1) != -1
                        ? AudioTimestamp.fromSeconds(info.getInt("duration"))
                        : null;
            }
        }
        catch (IOException e)
        {
            audioInfo.error = e.getMessage();
            e.printStackTrace();
        }
        catch (JSONException e)
        {
            audioInfo.error = e.getMessage();
            e.printStackTrace();
        }
        return audioInfo;
    }

    @Override
    public AudioStream asStream()
    {
        List<String> ytdlLaunchArgs = new ArrayList<>();
        List<String> ffmpegLaunchArgs = new ArrayList<>();
        if (ytdlLaunchArgsF == null)
            ytdlLaunchArgs.addAll(JDAPlayerConfig.getYOUTUBE_DL_LAUNCH_ARGS());
        else
        {
            ytdlLaunchArgs.addAll(ytdlLaunchArgsF);
            if (!ytdlLaunchArgs.contains("-q"))
                ytdlLaunchArgs.add("-q");
        }

        if (ffmpegLaunchArgsF == null)
            ffmpegLaunchArgs.addAll(JDAPlayerConfig.getFFMPEG_LAUNCH_ARGS());
        else
            ffmpegLaunchArgs.addAll(ytdlLaunchArgsF);

        ytdlLaunchArgs.add(url);    //specifies the URL to download.

        return new RemoteStream(ytdlLaunchArgs, ffmpegLaunchArgs);
    }

    @Override
    public File asFile(String path, boolean deleteIfExists) throws FileAlreadyExistsException, FileNotFoundException
    {
        if (path == null || path.isEmpty())
            throw new NullPointerException("Provided path was null or empty!");

        File file = new File(path);
        if (file.isDirectory())
            throw new IllegalArgumentException("The provided path is a directory, not a file!");
        if (file.exists())
        {
            if (!deleteIfExists)
            {
                throw new FileAlreadyExistsException("The provided path already has an existing file " +
                        " and the `deleteIfExists` boolean was set to false.");
            }
            else
            {
                if (!file.delete())
                    throw new UnsupportedOperationException("Cannot delete the file. Is it in use?");
            }
        }

        Thread currentThread = Thread.currentThread();
        FileOutputStream fos = new FileOutputStream(file);
        InputStream input = asStream();

        //Writes the bytes of the downloaded audio into the file.
        //Has detection to detect if the current thread has been interrupted to respect calls to
        // Thread#interrupt() when an instance of RemoteSource is in an async thread.
        //TODO: consider replacing with a Future.
        try
        {
            byte[] buffer = new byte[1024];
            int amountRead = -1;
            int i = 0;
            while (!currentThread.isInterrupted() && ((amountRead = input.read(buffer)) > -1))
            {
                fos.write(buffer, 0, amountRead);
            }
            fos.flush();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                input.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            try
            {
                fos.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return file;
    }
}
