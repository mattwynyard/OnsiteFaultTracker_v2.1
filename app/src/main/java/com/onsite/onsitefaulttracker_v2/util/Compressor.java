package com.onsite.onsitefaulttracker_v2.util;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Environment;
import android.util.Log;

import com.onsite.onsitefaulttracker_v2.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Compressor {
    private static final int BUFFER = 2048;

    private String[] _files;
    private String _zipFile;
    private CompressorListener mCompressorListener;
    private FileOutputStream mDest;

    /**
     * Interface for communicating with the parent fragment/activity
     */
    public interface CompressorListener {
        void dataRead(long _bytes);
    }

    /**
     * Sets the dropbox client listener
     *
     * @param listener
     */
    public void setCompressorListener(final CompressorListener listener) {
        mCompressorListener = listener;
    }

    public Compressor(String[] files, FileOutputStream dest) {
        _files = files;
        //_zipFile = zipFile;
        mDest = dest;
    }

    public void zip() {
        long _size;
        try  {
            BufferedInputStream origin = null;
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(mDest));
            byte data[] = new byte[BUFFER];

            for(int i=0; i < _files.length; i++) {
                Log.d("Compress", "Adding: " + _files[i]);
                _size = new File(_files[i]).length();
                //totalBytes += _size;
                FileInputStream fi = new FileInputStream(_files[i]);
                mCompressorListener.dataRead(_size);
                origin = new BufferedInputStream(fi, BUFFER);
                ZipEntry entry = new ZipEntry(_files[i].substring(_files[i].lastIndexOf("/") + 1));
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }
            //Log.d("Compress", "Bytes: " + totalBytes + "B");
            out.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}