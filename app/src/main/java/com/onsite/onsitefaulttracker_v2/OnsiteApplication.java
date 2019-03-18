package com.onsite.onsitefaulttracker_v2;

import android.app.Application;
import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;

import com.onsite.onsitefaulttracker_v2.connectivity.BLTManager;
import com.onsite.onsitefaulttracker_v2.util.BatteryUtil;
import com.onsite.onsitefaulttracker_v2.util.BitmapSaveUtil;
import com.onsite.onsitefaulttracker_v2.util.BusNotificationUtil;
import com.onsite.onsitefaulttracker_v2.util.CalculationUtil;
import com.onsite.onsitefaulttracker_v2.util.CameraUtil;
import com.onsite.onsitefaulttracker_v2.util.RecordUtil;
import com.onsite.onsitefaulttracker_v2.util.SettingsUtil;

/**
 * Created by hihi on 6/6/2016.
 *
 * The Application class for this application.
 * Sets up Singletons and Utility classes.
 */
public class OnsiteApplication extends Application {

    /**
     * On Create
     *
     * Sets up singletons and Utility classes
     */
    @Override
    public void onCreate()
    {
        super.onCreate();

        Fabric.with(this, new Crashlytics());
        // initialize the singletons used throughout this app
        SettingsUtil.initialize(this);
        CalculationUtil.initialize(this);
        CameraUtil.initialize(this);
        BatteryUtil.initialize(this);
        BitmapSaveUtil.initialize(this);
        RecordUtil.initialize(this);
        //BLEManager.initialize(this);
        BLTManager.initialize(this);
        BusNotificationUtil.initialize(this);
        //TcpConnection.initialize(this);
    }

}
