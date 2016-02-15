/*
 * Copyright 2015 HE5ED.com
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

package com.he5ed.lib.cloudprovider.utils;

import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * @hide
 */
public class FilesUtils {

    private final static String TAG = "FileUtils";

    /**
     *  Checks if external storage is available for read and write
     */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     *  Checks if external storage is available to at least read
     */
    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * Get unique filename
     *
     * @param dir directory where the file will reside
     * @param filename before change
     * @return original filename or modified filename if already taken
     */
    public static String getUniqueName(File dir, String filename) {
        // end if dir and filename is null
        if (dir == null || TextUtils.isEmpty(filename))
            return null;

        String uniqueName = filename;
        int i = 0;
        // get directory
        File innerDirs[] = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        });

        for (File innerDir : innerDirs) {
            uniqueName = getUniqueName(innerDir, uniqueName);
        }

        // check whether the path already exist to avoid override
        String[] filenames = dir.list();
        while (Arrays.asList(dir.list()).contains(uniqueName)) {
            // append ascending numbering to end of title
            if (filename.lastIndexOf('.') <= 0) {
                // filename with no extension
                uniqueName = filename + i++;
            } else {
                // Remove the last period and everything after it
                String ext = filename.substring(filename.lastIndexOf('.'), filename.length());
                uniqueName = filename.substring(0, filename.lastIndexOf(ext)) + i++ + ext;
            }
        }
        return uniqueName;
    }

    /**
     * Trim off the filename extension
     *
     * @param filename to be trimmed
     * @return filename without extension
     */
    public static String trimFileExtension(String filename) {

        if (filename.lastIndexOf('.') <= 0) {
            // filename with no extension
            return filename;
        } else {
            // Remove the last period and everything after it
            return filename.substring(0, filename.lastIndexOf('.'));
        }
    }

    /**
     * Copy file from source to dest using file channel (faster)
     *
     * @param source
     * @param dest
     */
    public static void copyFile(FileInputStream source, FileOutputStream dest) {
        FileChannel in = null;
        FileChannel out = null;

        try {
            in = source.getChannel();
            out = dest.getChannel();
            out.transferFrom(in, 0, in.size());
        } catch (IOException e) {
            Log.e(TAG, "Fail to copy file: ", e);
        } finally {
            try {
                in.close();
                out.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close file channel: ", e);
            }
        }
    }

    /**
     * Copy file from source to dest
     *
     * @param source
     * @param dest
     */
    public static void copyFile(InputStream source, OutputStream dest){
        try {
            byte[] buf = new byte[1024];
            int len;
            while ((len = source.read(buf)) > 0) {
                dest.write(buf, 0, len);
            }
        } catch (IOException e) {
            Log.e(TAG, "Fail to copy file: ", e);
        } finally {
            try {
                source.close();
                dest.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close file stream: ", e);
            }
        }
    }


    /**
     * Write string content to file
     *
     * @param dest
     * @param content
     */
    public static void writeFileContent(FileOutputStream dest, String content) {
        try {
            dest.write(content.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Error writing to file: ", e);
        } finally {
            try {
                dest.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close file stream: ", e);
            }
        }
    }

    /**
     * Convert file size to easy readable format
     *
     * @param size of the file
     * @return human readable file size string
     */
    public static String getFileSize(long size) {
        if (size <= 0)
            return "0";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
    
    /**
     * Get file mime type according to file extension
     *
     * @param file
     * @return mime type in string
     */
    public static String getFileType(File file) {
        String fileUrl = file.getPath();
        String fileExt = MimeTypeMap.getFileExtensionFromUrl(fileUrl);
        if (!TextUtils.isEmpty(fileExt)) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt);
        } else {
            return "application/octet-stream";
        }
    }

    /**
     * Get the available free internal storage in bytes
     *
     * @return bytes in long
     */
    public static long getInternalAvailableBytes() {
        StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return statFs.getAvailableBytes();
        } else {
            return statFs.getAvailableBlocks() * statFs.getBlockSize();
        }
    }
    
}
