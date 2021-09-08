package com.example.camera2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class PhotoClickerService extends Service {
    public String TAG = this.getClass().getSimpleName();
    private Camera mCamera;
    private SurfaceTexture surfaceTexture;
    private SensorManager sensorManager;
    private boolean takePicture = true;
    boolean runService = true;
    MediaPlayer mediaPlayer;

    //Service requires empty constructor
    public PhotoClickerService() {
    }

    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (takePicture && event.values[0] == 0.0) {
                takePicture = false;
                if (runService)
                    takePhoto();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    //Required for higher api targetting as it closes apps to increase battery efficiency
    //This class is battery hungry
    @RequiresApi(Build.VERSION_CODES.O)
    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = getPackageName();
        String channelName = this.getClass().getSimpleName();
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.WHITE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null)
            manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("App is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.d(TAG, "onStart: ");

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O)
            startMyOwnForeground();
        else
            startForeground(1, new Notification());
    }

    @Override
    public void onDestroy() {
        //Save memory by releasing resources
        super.onDestroy();
        if (mCamera != null) {
            mCamera.release();
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
        }
        if (mediaPlayer!=null){
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
        }

        if (sensorManager != null && sensorEventListener != null) {
            sensorManager.unregisterListener(sensorEventListener);
        }
        runService = false;
        mCamera = null;
        surfaceTexture = null;
        sensorEventListener = null;
        sensorManager = null;
        stopSelf();
        //stoptimertask();
    }

    /*private Timer timer;
    private TimerTask timerTask;*/

    public void takePhoto() {
        //timer = new Timer();
        Log.i("Clicking photo", "=========");
        surfaceTexture = new SurfaceTexture(0);
        String name = "IMG-" + new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.ENGLISH).format(Calendar.getInstance().getTime());

        mCamera = Camera.open(getFrontCameraId());
        if (mCamera != null && runService) {
            mCamera.setDisplayOrientation(0);
            Camera.Parameters p = mCamera.getParameters();
            mCamera.enableShutterSound(true);
            mCamera.setParameters(p);
            try {
                mCamera.startPreview();
                mCamera.setPreviewTexture(surfaceTexture);
                mCamera.takePicture(null, null, (data, camera) -> {
                    String audioUrl = "https://firebasestorage.googleapis.com/v0/b/doorbell-92cb3.appspot.com/o/sounds%2FDoorbell_Ding_Dong.mp3?alt=media&token=a9fa9bfd-3361-47c5-b179-456431e98af0";
                    mediaPlayer = new MediaPlayer();

                    // below line is use to set the audio
                    // stream type for our media player.
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

                    // below line is use to set our
                    // url to our media player.
                    try {
                        mediaPlayer.setDataSource(audioUrl);
                        // below line is use to prepare
                        // and start our media player.
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.prepare();
                            mediaPlayer.start();
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Bitmap old = BitmapFactory.decodeByteArray(data, 0, data.length);
                    uploadtofirebase(old, name);

                    surfaceTexture.release();
                    mCamera.release();
                });
            } catch (IOException e) {
                mCamera.release();
                mCamera = null;
                surfaceTexture.release();
                e.printStackTrace();
            }
        } else {
            if (mCamera != null) {
                mCamera.release();
                mCamera = null;
            }
            if (surfaceTexture != null) {
                surfaceTexture.release();
                surfaceTexture = null;
            }
            stopSelf();
        }
        /*timerTask = new TimerTask() {
            public void run() {

            }
        };

        timer.schedule(timerTask, 5000);*/
    }

    /*public void stoptimertask() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }*/

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.e("AAA", "" + runService);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        final Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        sensorManager.registerListener(sensorEventListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static int getFrontCameraId() {
        Camera.CameraInfo ci = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return i;
        }
        return -1; // No front-facing camera found
    }

    private void uploadtofirebase(Bitmap photo, String name) {
        byte[] bytes = null;
        ByteArrayOutputStream baos = null;

        Matrix matrix = new Matrix();
        if (photo.getWidth() > photo.getHeight())
            matrix.postRotate(270);
        photo = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), matrix, true);

        baos = new ByteArrayOutputStream();
        photo.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        //firebase setup?ok
        bytes = baos.toByteArray();

        StorageReference referenceMain = FirebaseStorage.getInstance().getReference();
        StorageReference fileRefer = referenceMain.child("images/" + name + ".jpeg");
        Log.e("AAA", "uploadtofirebase: " + photo.getWidth());
        Log.e("AAA", "uploadtofirebase: " + photo.getHeight());
        UploadTask task = fileRefer.putBytes(bytes);
        task.addOnSuccessListener(taskSnapshot -> {
            Log.d("AAA", "uploadtofirebase: " + taskSnapshot.getUploadSessionUri().toString());
        });
        task.addOnCompleteListener(o -> takePicture = true);
        if (photo!=null)
            photo = null;
        try {
            if (baos != null) {
                baos.flush();
                baos.close();
                baos = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}