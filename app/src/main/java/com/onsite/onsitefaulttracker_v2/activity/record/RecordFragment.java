package com.onsite.onsitefaulttracker_v2.activity.record;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.onsite.onsitefaulttracker_v2.R;
import com.onsite.onsitefaulttracker_v2.activity.BaseFragment;
import com.onsite.onsitefaulttracker_v2.connectivity.BLTManager;
import com.onsite.onsitefaulttracker_v2.model.Record;
import com.onsite.onsitefaulttracker_v2.model.notifcation_events.BLTStartRecordingEvent;
import com.onsite.onsitefaulttracker_v2.model.notifcation_events.BLTStopRecordingEvent;
import com.onsite.onsitefaulttracker_v2.ui.AutoFitTextureView;
import com.onsite.onsitefaulttracker_v2.ui.VerticalSeekBar;
import com.onsite.onsitefaulttracker_v2.util.BatteryUtil;
import com.onsite.onsitefaulttracker_v2.util.BitmapSaveUtil;
import com.onsite.onsitefaulttracker_v2.util.BusNotificationUtil;
import com.onsite.onsitefaulttracker_v2.util.CameraUtil;
import com.onsite.onsitefaulttracker_v2.util.MessageUtil;
import com.onsite.onsitefaulttracker_v2.util.RecordUtil;
import com.onsite.onsitefaulttracker_v2.util.SettingsUtil;
import com.onsite.onsitefaulttracker_v2.util.ThreadUtil;
import com.squareup.otto.Subscribe;

import java.util.Date;

/**
 * Created by hihi on 6/12/2016.
 *
 * Record Fragment is the activity which performs the bulk of the work for this app.
 * It takes a photo every set time interval and saves them to storage with the date stamp
 * filename
 */
public class RecordFragment extends BaseFragment implements CameraUtil.CameraConnectionListener,
        SeekBar.OnSeekBarChangeListener {

    // The tag name for this class
    private static final String TAG = RecordFragment.class.getSimpleName();

    // The interval for which to sound a warning for low disk space or battery
    private long SOUND_WARNING_INTERVAL = 5000;

    // The interval for checking the battery level
    private long CHECK_BATTERY_INTERVAL = 10000;

    // The level that if the battery falls below an alarm will sound
    private final float LOW_BATTERY_ALARM_LEVEL = 15.0f;

    // If this number of attempted frame captures in a row are blank,
    // show an error and stop recording.
    private final int BLANK_FRAMES_BEFORE_ERROR = 10;

    String[] PERMISSIONS = {
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.READ_PHONE_STATE
    };

    // The autoFitTextureView displays the camera view finder on the screen
    private AutoFitTextureView mTextureView;

    // The overlay view which will hide the view finder
    // To hide the view finder, set overlay view to visible.
    private View mOverlayView;

    // The TextView which shows the current count of the taken photos
    private TextView mPhotoCountTextView;

    // The seek bar for the level of exposure
    private VerticalSeekBar mExposureSeekBar;

    // The interval time between frames
    private long mIntervalMillis;

    // System time that recording started
    private long mStartedRecordingTime;

    // The record that is currently being recorded
    private Record mRecord;

    // Is it currently recording
    private boolean mRecording;

    // Has the low disk space error been displayed?
    private boolean mDisplayedLowDiskError;

    // Has the low battery error been displayed?
    private boolean mDisplayedLowBatteryError;

    // The last time that a warning sound was played
    private long mLastWarningSoundedTime;

    // The last time the battery level was checked
    private long mLastBatteryCheckedTime;

    // The number of consecutive blank frames,  if this increases too high
    // display an error and close the fragment
    private int mConsecutiveBlankFrames;

    /**
     * instantiate and return an instance of this fragment
     *
     * @return
     */
    public static RecordFragment createInstance() {
        return new RecordFragment();
    }

    /**
     * Returns the display title for this fragment
     *
     * @return
     */
    @Override
    protected String getDisplayTitle() {
        return getString(R.string.record_title);
    }

    /**
     * Returns the layout resource for this fragment
     *
     * @return
     */
    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_record;
    }

    /**
     * On create view, Override this in each extending fragment to implement initialization for that
     * fragment.
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (view != null) {
            mDisplayedLowDiskError = false;
            mTextureView = (AutoFitTextureView)view.findViewById(R.id.camera_texture_view);
            mOverlayView = view.findViewById(R.id.overlay_view);
            mExposureSeekBar = (VerticalSeekBar)view.findViewById(R.id.exposure_seek_bar);
            mExposureSeekBar.setVisibility(View.INVISIBLE);//TODO:TEMPHACK
            mPhotoCountTextView = (TextView)view.findViewById(R.id.photo_count);
            mConsecutiveBlankFrames = 0;

            mIntervalMillis = SettingsUtil.sharedInstance().getPictureFrequency();
            updateExposureSeekPosition();
            mExposureSeekBar.setOnSeekBarChangeListener(this);

            // Initialize record to the current record
            mRecord = RecordUtil.sharedInstance().getCurrentRecord();

            // Update the textview which displays the number of photos taken
            updatePhotoCountText();
        }
        return view;
    }

    /**
     *  Called when fragment resumes,  starts the background thread which takes photos every
     *  specified interval.
     */
    @Override
    public void onResume() {
        super.onResume();
        //BLTManager.sharedInstance().sendMessge("");
        //MessageUtil.sharedInstance().setRecording("R");
        Log.i(TAG, "RECORD:RESUMED");
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            CameraUtil.sharedInstance().openCamera(mTextureView.getWidth(), mTextureView.getHeight(), getActivity(), mTextureView);
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        // Register to receive bluetooth notifications
        BusNotificationUtil.sharedInstance().getBus().register(this);
    }

    /**
     * Called when the fragment is paused,
     * closes the camera and stops the background thread that it is running on
     */
    @Override
    public void onPause() {
        super.onPause();

        Log.i(TAG, "RECORD:PAUSED");
        if (mStartedRecordingTime > 0) {
            long recordedTime = new Date().getTime() - mStartedRecordingTime;
            mRecord.totalRecordTime += recordedTime;
        }
        CameraUtil.sharedInstance().closeCamera();
        RecordUtil.sharedInstance().saveCurrentRecord();
        BusNotificationUtil.sharedInstance().getBus().unregister(this);
        if (BLTManager.sharedInstance().getState() == 3) {
            BLTManager.sharedInstance().sendMessge("");
        }
    }

    /**
     * diaplays the number of photos taken
     */
    private void updatePhotoCountText() {
        mPhotoCountTextView.setText(String.format("%d", mRecord.photoCount));
    }

    /**
     * Update the exposure seek bars position
     */
    private void updateExposureSeekPosition() {
       mExposureSeekBar.setProgress(SettingsUtil.sharedInstance().getCurrentExposureAsPercentage());
    }

    /**
     * Take a snapshot and save it to the drive
     */
    private void takeSnapshot() {
        // Verify if recording or not,  if not dont take a snap,
        // just returns
        if (!mRecording) {
            return;
        }

        // Grab a bitmap from the current display on the texture view
        final Bitmap snapBitmap = mTextureView.getBitmap();
        // If the bitmap is valid save it as the next image using the BitmapSaveUtil
       //TODO:TEMPHACK if (snapBitmap != null && snapBitmap.getWidth() > 0 && snapBitmap.getHeight() > 0) {
        if (snapBitmap != null && snapBitmap.getHeight() > 0 && snapBitmap.getWidth() > 0) {
            mConsecutiveBlankFrames = 0;
            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            float ratio = (!isLandscape) ?
                            ((float)snapBitmap.getHeight()/(float)snapBitmap.getWidth()) :
                            ((float)snapBitmap.getWidth()/(float)snapBitmap.getHeight());
            BitmapSaveUtil.SaveBitmapResult saveResult = BitmapSaveUtil.sharedInstance().saveBitmap(snapBitmap, mRecord, ratio, isLandscape);
            long currentTime = new Date().getTime();

            // Check the result of saving the bitmap for low dis space or error
            // because of no disk space
            if (saveResult != BitmapSaveUtil.SaveBitmapResult.Error) {
                if (saveResult == BitmapSaveUtil.SaveBitmapResult.SaveLowDiskSpace) {
                    if (!mDisplayedLowDiskError) {
                        displayLowDiskSpaceError();
                        mDisplayedLowDiskError = true;
                    }
                    if (currentTime - mLastWarningSoundedTime >= SOUND_WARNING_INTERVAL) {
                        mLastWarningSoundedTime = currentTime;
                        //playWarningSound();
                    }
                } else {
                    if (BLTManager.sharedInstance().getState() == 3) {
                        MessageUtil.sharedInstance().setError(0);
                    }
                }
                mRecord.photoCount++;
                Log.i(TAG, "Photo Count: " + mRecord.photoCount);
                updatePhotoCountText();

            } else {
                // There is not enough disk space to save any more photos,
                // display an error and then close the fragment
                onOutOfDiskSpaceError();
            }

            // Check the battery level every now and then to make sure it isn't running low
            if (currentTime - mLastBatteryCheckedTime >= CHECK_BATTERY_INTERVAL) {
                mLastBatteryCheckedTime = currentTime;
                float currentBatteryLevel = BatteryUtil.sharedInstance().getBatteryLevel();

                if (BLTManager.sharedInstance().getState() == 3) { //STATE_CONNECTED
                    int batteryLevel = Math.round(currentBatteryLevel);
                    MessageUtil.sharedInstance().setBattery(batteryLevel);

                } else {
                    if (currentBatteryLevel <= LOW_BATTERY_ALARM_LEVEL) {
                        if (!mDisplayedLowBatteryError) {
                            displayLowBatteryError();
                            mDisplayedLowBatteryError = true;
                        }
                        //playWarningSound();
                    }
                }
            }

        } else {
            // Every time an invalid frame is captured, increment consecutive blank frames.
            // If the consecutive blank frames rise too high, stop the recording and display
            // an error to the user.
            mConsecutiveBlankFrames++;
            if (mConsecutiveBlankFrames >= BLANK_FRAMES_BEFORE_ERROR) {
                onRecordingError();
            }
        }
    }
    /**
     * Action when an error occurs while recording,
     * such as frames not being captured.
     * Stops recording and displays an error to the user.
     */
    private void onRecordingError() {
        stopRecording();

        if (BLTManager.sharedInstance().getState() == 3) {
            //BLTManager.sharedInstance().sendPhoto("E:CAMERA ERROR,", null);
            MessageUtil.sharedInstance().setError(3);
        } else {

            Log.e(TAG, "*******************************************************");
            Log.e(TAG, "* ERROR                                               *");
            Log.e(TAG, "*******************************************************");
            Log.e(TAG, "* RECORDING ERROR                                     *");
            Log.e(TAG, "*******************************************************");

//            new AlertDialog.Builder(getActivity())
//                    .setTitle(getString(R.string.record_error_title))
//                    .setMessage(getString(R.string.record_error_message))
//                    .setPositiveButton(getString(android.R.string.ok), null)
//                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
//                        @Override
//                        public void onDismiss(DialogInterface dialog) {
//                            getActivity().onBackPressed();
//                        }
//                    })
//                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
//                        @Override
//                        public void onCancel(DialogInterface dialog) {
//                            getActivity().onBackPressed();
//                        }
//                    })
//                    .show();
        }
    }

    /**
     * Action when there is no disk space left to record any frames.
     * Stops recording and displays an error to the user.
     */
    private void onOutOfDiskSpaceError() {
        if (BLTManager.sharedInstance().getState() == 3) {
            //BLTManager.sharedInstance().sendPhoto("M:OUT OF CAMERA SPACE,", null);
            MessageUtil.sharedInstance().setError(2);
            stopRecording();
        } else {
            stopRecording();
            new AlertDialog.Builder(getActivity())
                    .setTitle(getString(R.string.record_no_disk_space_title))
                    .setMessage(getString(R.string.record_no_disk_space_message))
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            getActivity().onBackPressed();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            getActivity().onBackPressed();
                        }
                    })
                    .setPositiveButton(getString(android.R.string.ok), null)
                    .show();

            Log.e(TAG, "*******************************************************");
            Log.e(TAG, "* ERROR                                               *");
            Log.e(TAG, "*******************************************************");
            Log.e(TAG, "* NO DISK SPACE LEFT                                  *");
            Log.e(TAG, "*******************************************************");
        }
    }

    /**
     * Play the warning sound
     */
    private void playWarningSound() {
        MediaPlayer mp = MediaPlayer.create(getActivity().getApplicationContext(), R.raw.a_tone);
        mp.start();

        Log.e(TAG, "*******************************************************");
        Log.e(TAG, "* WARNING                                             *");
        Log.e(TAG, "*******************************************************");
        Log.e(TAG, "* CHECK DEVICE FOR WARNING                            *");
        Log.e(TAG, "*******************************************************");
    }

    /**
     * Display the low disk space warning to the user
     */
    private void displayLowDiskSpaceError() {

        if (BLTManager.sharedInstance().getState() == 3) {
            MessageUtil.sharedInstance().setError(1);
        } else {
            new AlertDialog.Builder(getActivity())
                    .setTitle(getString(R.string.record_low_disk_space_dialog_title))
                    .setMessage(getString(R.string.record_low_disk_space_dialog_message))
                    .setPositiveButton(getString(android.R.string.ok), null)
                    .show();

            Log.e(TAG, "*******************************************************");
            Log.e(TAG, "* WARNING                                             *");
            Log.e(TAG, "*******************************************************");
            Log.e(TAG, "* LOW DISK SPACE                                      *");
            Log.e(TAG, "*******************************************************");
        }
    }

    /**
     * Display the low battery warning to the user
     */
    private void displayLowBatteryError() {

        if (BLTManager.sharedInstance().getState() == 3) {
            //BLTManager.sharedInstance().sendPhoto("B:LOW CAMERA BATTERY,", null);
        } else {
            new AlertDialog.Builder(getActivity())
                    .setTitle(getString(R.string.record_low_battery_dialog_title))
                    .setMessage(getString(R.string.record_low_battery_dialog_message))
                    .setPositiveButton(getString(android.R.string.ok), null)
                    .show();

            Log.e(TAG, "*******************************************************");
            Log.e(TAG, "* WARNING                                             *");
            Log.e(TAG, "*******************************************************");
            Log.e(TAG, "* LOW BATTERY                                         *");
            Log.e(TAG, "*******************************************************");
        }
    }

    /**
     * schedules the next frame to be snapped after the set time interval
     */
    private void scheduleNextFrame() {
        // Verify recording is still enabled, if not dont schedule next frame
        // just return
        if (!mRecording) {
            return;
        }

        // Schedule next frame to be snapped after specified time interval
        ThreadUtil.executeOnMainThreadDelayed(new Runnable() {
            @Override
            public void run() {
                scheduleNextFrame();
                takeSnapshot();
            }
        }, mIntervalMillis);
    }

    public boolean isRecording() {
        return mRecording;
    }

    /**
     * Start recording snap shots
     */
    private void startRecording() {
        if (!mRecording) {
            Log.i(TAG, "Start recording called");
            mStartedRecordingTime = new Date().getTime();
            mRecording = true;
           MessageUtil.sharedInstance().setRecording("R");
            scheduleNextFrame();
        } else {
            Log.i(TAG, "Start recording called but already recording");
        }
    }

    /**
    /**
     * Stop recording snap shots
     */
    private void stopRecording() {
        if (mRecording) {
            Log.i(TAG, "Stop recording called");
            MessageUtil.sharedInstance().setRecording("N");
            //BLTManager.sharedInstance().sendMessge("");
            mRecording = false;
        } else {
            Log.i(TAG, "Stop recording called but already recording");
        }
    }

    // *******************************************************************
    //  Callback functions from camera util
    // *******************************************************************
    @Override
    public void onCameraOpened() {

        Log.d(TAG, "Camera Opened");

    }

    @Override
    public void onCameraDisconnected() {
        Log.d(TAG, "Camera Disconnected");
    }

    @Override
    public void onCameraError(int error) {
        Log.d(TAG, "Camera Error");

    }

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            CameraUtil.sharedInstance().openCamera(width, height, getActivity(), mTextureView);

            //Log.d(TAG, "Texture available called");
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            CameraUtil.sharedInstance().configureTransform(width, height);
            //Log.d(TAG, "Texture size changed called");
            startRecording();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            //Log.d(TAG, "Texture destroyed called");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            //Log.d(TAG, "Texture updated called");
        }

    };


    // ************************************************************
    //  Seek Bar delegate methods
    // ************************************************************
    /**
     * Action when the value of the seek bar has been changed
     *
     * @param seekBar   The seek bar that the event was sent from
     * @param progress  the progress value that the seek bar has updated up to
     * @param fromUser  true if the progress was changed via a user action
     */
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar == mExposureSeekBar) {
            // The Exposure value has been changed
            SettingsUtil.sharedInstance().setCurrentExposureFromPercentage(progress);
            CameraUtil.sharedInstance().onExposureSettingUpdated();
        }
    }

    /**
     * Action when the user starts move the value on the seek bar
     *
     * @param seekBar  The seek bar that this event occurred on
     */
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    /**
     * Action when the user has stopped moving the value on the seek bar
     *
     * @param seekBar  The seek bar that this event occurred on
     */
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    // **********************************************************
    //  Notifications
    // **********************************************************
    /**
     *
     * Event from when user elects to pause recording
     *
     * @param event
     */

    @Subscribe
    public void onBLTStartRecordingEvent(BLTStartRecordingEvent event) {
        Log.d(TAG, "Start recording called from BLTEvent");
        startRecording();
    }
    /**
     * Event from when user elects to pause recording
     *
     * @param event
     */
    @Subscribe
    public void onBLTStopRecordingEvent(BLTStopRecordingEvent event) {
        Log.d(TAG, "Stop recording called from BLTEvent");
        stopRecording();
        getActivity().onBackPressed();
    }
}
