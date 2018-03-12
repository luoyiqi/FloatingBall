package com.chenyee.stephenlau.floatingball.services;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodManager;

import com.chenyee.stephenlau.floatingball.FloatBallManager;
import com.chenyee.stephenlau.floatingball.R;
import com.chenyee.stephenlau.floatingball.activities.MainActivity;

import java.lang.reflect.Method;
import java.util.List;

import static com.chenyee.stephenlau.floatingball.util.StaticStringUtil.EXTRA_TYPE;


/**
 * Accessibility services should only be used to assist users with disabilities in using Android devices and apps.
 *  Such a service can optionally随意地 request the capability能力 for querying the content of the active window.
 *
 * Accept the intent from Main activity,
 * Created by wangxiandeng on 2016/11/25.
 */

public class FloatingBallService extends AccessibilityService {
    private static final String TAG =FloatingBallService.class.getSimpleName();

    public static final int TYPE_ADD = 0;
    public static final int TYPE_DEL = 1;
    public static final int TYPE_IMAGE =2;
    public static final int TYPE_USE_BACKGROUND =4;
    public static final int TYPE_UPDATE_DATA =5;

    private FloatBallManager mFloatBallManager;
    private NotificationManager mNotificationManager;

    private boolean hasSoftKeyboardShow=false;

    public static Intent getStartIntent(Context context) {
        return new Intent(context, FloatingBallService.class);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected: ");
        if(mFloatBallManager==null)
            mFloatBallManager = FloatBallManager.getInstance();

        //TEST
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        inputMethodSate(getApplicationContext());
    }
    /**
     * According to the state of input method, move the floatingBall view.
     * @param context Context
     */
    private void inputMethodSate(Context context) {
        //得到默认输入法包名
        String defaultInputName = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        defaultInputName = defaultInputName.substring(0, defaultInputName.indexOf("/"));
        boolean isInputing = false;
        if(android.os.Build.VERSION.SDK_INT > 20) {//Work
            try{
                InputMethodManager imm = (InputMethodManager) context.getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                Class clazz = imm.getClass();
                Method method = clazz.getMethod("getInputMethodWindowVisibleHeight", null);
                method.setAccessible(true);
                int height = (Integer) method.invoke(imm, null);
                Log.d("LOG", "height == "+height);
                if(height > 100) {
                    isInputing = true;
                }
            }catch(Exception e) {
                e.printStackTrace();
            }
        }else {//应该不work
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();

            for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                    if(appProcess.processName.equals(defaultInputName)) {
                        isInputing = true;
                        break;
                    }
                }
            }
        }
        if(isInputing) {
            if (!hasSoftKeyboardShow)
                mFloatBallManager.moveBallViewUp();
            hasSoftKeyboardShow=true;
        }else {
            if(hasSoftKeyboardShow)
                mFloatBallManager.moveBallViewDown();
            hasSoftKeyboardShow=false;
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt: ");
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: ");
        mFloatBallManager.saveFloatBallData();
    }

    //    Called by the system every time a client explicitly starts the service by calling startService(Intent),
    // providing the arguments it supplied and a unique integer token representing the start request.
    // Do not call this method directly.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if(intent != null ) {
            //mFloatBallManager的判断是因为生命周期有时候有问题
            if(mFloatBallManager==null)
                mFloatBallManager = FloatBallManager.getInstance();

            Bundle data = intent.getExtras();
            if (data != null) {
                int type = data.getInt(EXTRA_TYPE);

                if (type == TYPE_ADD) {
                    mFloatBallManager.addBallView(FloatingBallService.this);
                }
                if(type== TYPE_DEL){
                    mFloatBallManager.removeBallView();//内部有mFloatBallManager.saveFloatBallData();
                }
                //intent中传地址
                if (type == TYPE_IMAGE) {
                    mFloatBallManager.setBackgroundPic(data.getString("imagePath"));
                }

                if(type== TYPE_USE_BACKGROUND){
                    mFloatBallManager.setUseBackground(data.getBoolean("useBackground"));
                }
                if(type==TYPE_UPDATE_DATA){
                    mFloatBallManager.updateBallViewParameter();
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public void hideBall(){
        if(mFloatBallManager==null)
            mFloatBallManager = FloatBallManager.getInstance();

        mFloatBallManager.removeBallView();//内部有mFloatBallManager.saveFloatBallData();
        sendNotification();

    }
    private void sendNotification() {
        String contentTitle = getString(R.string.hideNotificationContentTitle);
        String contentText = getString(R.string.hideNotificationContentText);

        if(mNotificationManager==null){
            mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        Intent intent = new Intent(this, FloatingBallService.class);
        Bundle data = new Bundle();
        data.putInt(EXTRA_TYPE, FloatingBallService.TYPE_ADD);
        intent.putExtras(data);
        PendingIntent pintent = PendingIntent.getService(this, 0, intent, 0);

        if (Build.VERSION.SDK_INT >= 26) {
            String channelID = "1";
            String channelName = "channel_name";
            NotificationChannel mChannel = new NotificationChannel(channelID, channelName, NotificationManager.IMPORTANCE_LOW);

            mNotificationManager.createNotificationChannel(mChannel);

            Notification notification = new Notification.Builder(this, channelID)
                    .setSmallIcon(R.mipmap.ic_launcher_app)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher_app))
                    .setContentTitle(contentTitle)
                    .setContentText(contentText)
                    .setAutoCancel(true)
                    .setContentIntent(pintent)
                    .build();
            mNotificationManager.notify(1, notification);

        }else{
            Notification notification = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.mipmap.ic_launcher_app)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher_app))
                    .setContentTitle(contentTitle)
                    .setContentText(contentText)
                    .setAutoCancel(true)
                    .setContentIntent(pintent)
                    .build();
            mNotificationManager.notify(1, notification);
        }
    }
}
