package com.onsite.onsitefaulttracker_v2.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import com.onsite.onsitefaulttracker_v2.connectivity.BLTManager;
import com.onsite.onsitefaulttracker_v2.model.Record;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by hihi on 6/21/2016.
 *
 * Utility class which will handle saving bitmaps to storage
 */
public class BitmapSaveUtil {

    // The tag name for this utility class
    private static final String TAG = BitmapSaveUtil.class.getSimpleName();

    // The low disk space threshold
    private static final long LOW_DISK_SPACE_THRESHOLD = 102400L;

    private static final double THUMBNAIL_REDUCTION = 0.25;

    private int totalBitMapTime = 0;
    private int totalBitMapCount = 0;
    private double avgSaveTime = 0;

    // An enum which has all the SaveBitmapResult values
    public enum SaveBitmapResult {
        Save,
        SaveLowDiskSpace,
        Error
    }

    private AtomicInteger count;

    private Calendar mCal = Calendar.getInstance();
    private TimeZone mTz = mCal.getTimeZone();
    String mMesageDateString;

    // The format of file names when converted from a date
    private static final String FILE_DATE_FORMAT = "yyMMdd_HHmmss";

    // A static instance of the bitmap save utilities
    private static BitmapSaveUtil sBitmapSaveUtil;

    // Store the application context for access to storage
    private static Context mContext;

    /**
     * Store the appication context for access to storage
     *
     * @param context
     */
    public static void initialize(final Context context) {
        sBitmapSaveUtil = new BitmapSaveUtil(context);
    }

    /**
     * Contructor, to be called internally via. initialize
     * @param context
     */
    private BitmapSaveUtil(final Context context) {
        mContext = context;
        count = new AtomicInteger(0);
        mCal = Calendar.getInstance();
        mTz = mCal.getTimeZone();
    }

    /**
     * Returns a shared instance of BitmapSaveUtil
     * @return
     */
    public static BitmapSaveUtil sharedInstance() {
        if (sBitmapSaveUtil != null) {
            return sBitmapSaveUtil;
        } else {
            throw new RuntimeException("BitmapSaveUtil must be initialized in the " +
                    "Application class before use");
        }
    }

    /**
     * Saves a bitmap to storage taking in a temp number for now for the filename
     *
     * @param bitmapToSave
     * @param record
     * @param widthDivisor a factor to divide the width by
     */
    public SaveBitmapResult saveBitmap(final Bitmap bitmapToSave,
                                       final Record record,
                                       final float widthDivisor,
                                       final boolean isLandscape) {
        Date nowDate = new Date();
        String halfAppend = "";
        boolean useHalfAppend = (SettingsUtil.sharedInstance().getPictureFrequency() % 1000 > 0);
//        if (useHalfAppend && (time % 1000) >= 500) {
//            halfAppend = "_500";
//        } else {
//            halfAppend = "_000";
//        }
        totalBitMapCount++;
        if (count.get() == 10) {
            count.set(0);
        }
        halfAppend = Integer.toString(count.getAndIncrement());

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(FILE_DATE_FORMAT);
        String dateString = simpleDateFormat.format(nowDate);

        SimpleDateFormat messageDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        mMesageDateString = messageDateFormat.format(nowDate);


        String cameraIdPrefix = SettingsUtil.sharedInstance().getCameraId();
        if (cameraIdPrefix == null) {
            cameraIdPrefix = "NOID";
        }
        cameraIdPrefix += "_";
        final String filename = cameraIdPrefix + "IMG" + dateString + "-" + halfAppend;

        long availableSpace = CalculationUtil.sharedInstance().getAvailableStorageSpaceKB();
        if (availableSpace <= 1024) {
            return SaveBitmapResult.Error;
        }

        Bitmap resizedBitmap;

        ThreadUtil.executeOnNewThread(new Runnable() {
            @Override
            public void run() {
                String path = RecordUtil.sharedInstance().getPathForRecord(record);
                File folder = new File(path);
                if (!folder.exists()) {
                    Log.e(TAG, "Error saving snap, Record path does not exist");
                    return;
                }
                File file = new File(path + "/", filename + ".jpg");
                //File fileResize = new File(path + "/", filename + "_R.jpg");
                try {
                    long start = System.currentTimeMillis();
                    OutputStream fOutputStream = new FileOutputStream(file);
                    //OutputStream fOutputStreamResize = new FileOutputStream(fileResize);

                    float reductionScale = CalculationUtil.sharedInstance()
                            .estimateScaleValueForImageSize();
                    int outWidth = Math.round(bitmapToSave.getHeight() / widthDivisor);
                    int outHeight = bitmapToSave.getHeight();
                    Bitmap sizedBmp = Bitmap.createScaledBitmap(bitmapToSave,
                            Math.round(outWidth * reductionScale), Math.round(outHeight *
                                    reductionScale), true);

                    Matrix matrix = new Matrix();
                    if (isLandscape) {
                        matrix.postRotate(-90);
                    }
                    Bitmap rotatedBitmap = Bitmap.createBitmap(sizedBmp, 0, 0,
                            sizedBmp.getWidth(), sizedBmp.getHeight(), matrix, true);
                    if (rotatedBitmap == null) {
                        return;
                    }
                    sizedBmp.recycle();
                    ByteArrayOutputStream photo = new ByteArrayOutputStream();
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, CalculationUtil
                            .sharedInstance().estimateQualityValueForImageSize(), fOutputStream);

                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, CalculationUtil
                            .sharedInstance().estimateQualityValueForImageSize(), photo);

//                    Bitmap resizedBitmap = Bitmap.createScaledBitmap(rotatedBitmap,
//                            (int)(rotatedBitmap.getWidth() * THUMBNAIL_REDUCTION),
//                            (int)(rotatedBitmap.getHeight() * THUMBNAIL_REDUCTION), true);
//                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, CalculationUtil
//                            .sharedInstance().estimateQualityValueForImageSize(),
//                            photo);
                    rotatedBitmap.recycle();
                    //resizedBitmap.recycle();
                    bitmapToSave.recycle();

                    fOutputStream.flush();
                    fOutputStream.close();
                    //fOutputStreamResize.flush();
                    //fOutputStreamResize.close();

                    long finish = System.currentTimeMillis();
                    Log.d(TAG, "Photo save time: " + (finish - start));
                    Log.d(TAG, "Bitmap count: " + totalBitMapCount);
                    totalBitMapTime += (finish - start);
                    Double time = (double)totalBitMapTime / totalBitMapCount;
                    avgSaveTime = new BigDecimal(time).setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();

                    fileComplete(filename, photo);

                    Log.d(TAG, "Avergage Photo save time: " + (double)(totalBitMapTime / totalBitMapCount));

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
                bitmapToSave.recycle();
            }
        });

        //final String message = buildMessage(mMesageDateString, filename, avgSaveTime);

        if (availableSpace <= LOW_DISK_SPACE_THRESHOLD) {
            return SaveBitmapResult.SaveLowDiskSpace;
        } else {
            return SaveBitmapResult.Save;
        }
    }

    private void fileComplete(String filename, ByteArrayOutputStream photo) {
        Log.d(TAG, "JPEG written to disk");
        final String message = buildMessage(mMesageDateString, filename, avgSaveTime);
        sendPhoto(message, photo);
    }

    /**
     *  Builds a message string using StringBuilder ready for sending through bluetooth
     * @param dateTime - a date time stamp the photo was taken
     * @param file - the filename of the photo
     * @param saveTime - the time taken to prepare the bitmap (testing only)
     * @return - a string with relevant data ready to be sent through bluetooth
     */
    private String buildMessage(String dateTime, String file, Double saveTime) {

        StringBuilder messageString = new StringBuilder();
        messageString.append("T:" + dateTime + "|");
        messageString.append(file + "|");
        messageString.append(saveTime + ",");
        String message = messageString.toString();
        Log.d(TAG, "String builder path: " +  message);
        return message;
    }

    private void sendMessage(final String message ) {
        BLTManager.sharedInstance().sendMessage(message);
    }

    /**
     *  sends a message and photo through bluetooth, see sendPhoto method in BLTManger for the
     *  actual algorithm for perapring the data
     * @param message - the message to be sent
     * @param photo - a byte array containing the photo data
     */
    private void sendPhoto(final String message, final ByteArrayOutputStream photo) {
        BLTManager.sharedInstance().sendPhoto(message, photo);
    }
}
