package com.sana.android.plugin.hardware;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import com.sana.android.plugin.application.CommManager;
import com.sana.android.plugin.communication.MimeType;
import com.sana.android.plugin.data.BinaryDataWithPollingEvent;
import com.sana.android.plugin.data.DataWithEvent;
import com.sana.android.plugin.data.event.BytePollingDataEvent;

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URISyntaxException;


/**
 * Created by zhaoyue on 9/14/14.
*/
public class BluetoothAudioDevice implements GeneralDevice {
    private ContentResolver resolver;
    private static final String TAG = "BluetoothAudioRecordTest";
    private static final String LOG_TAG = "Is it here?";
    private AudioManager mAudioManager;
    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;
    private static String mFileName = "";
    private Context mContext;
    private int audioEncoder;
    private int audioSource;
    private int outputFormat;

    // set the current output file name
    public BluetoothAudioDevice(){
        this.mFileName=Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/bluetoothtest.3gp";
    }

    public DataWithEvent prepare() {
        mRecorder = new MediaRecorder();
        mRecorder.setOutputFile(mFileName);
        startBluetoothMic();
        try {
            InputStream is = new FileInputStream(mFileName);
            DataWithEvent result = new BinaryDataWithPollingEvent(
                    Feature.BLUETOOTH_MICROPHONE,
                    MimeType.AUDIO,
                    CommManager.getInstance().getUri(),
                    this,
                    is,
                    BytePollingDataEvent.BUFFER_SIZE_SMALL
            );
            return result;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void begin() {
        if (mRecorder != null) {
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            try {
                mRecorder.prepare();
            } catch (IOException e) {
                Log.e(LOG_TAG, "prepare() failed");
            }
            mRecorder.start();
        }
    }

    @Override
    public void stop() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
        moveData();
    }

    public void moveData() {
        try {
            // if Sana is requesting text, the Uri will be null
            System.out.println("here: "+ CommManager.getInstance().getMimeType());
            if(CommManager.getInstance().getUri() == null ||
               CommManager.getInstance().getMimeType().toString().equals("text/plain")) {
                // do not move anything if required is plain text
                System.out.println("Inside move data");
                return ;
            }
            Log.d(
                    "AudioRecordDevice",
                    CommManager.getInstance().getUri().toString()
            );
            FileInputStream is = new FileInputStream(mFileName);
            OutputStream os = resolver.openOutputStream(CommManager.getInstance().getUri());
            IOUtils.copy(is, os);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reset() {
        stop();
        File file = new File(mFileName);
        boolean deleted = file.delete();
        if(deleted){
            mFileName = null;
        }
    }

    // mContext is essential for checking bluetooth connectivity
    public void setCaptureSetting(CaptureSetting setting) {
        this.audioEncoder = setting.getAudioEncoder();
        this.audioSource = setting.getAudioSource();
        this.outputFormat = setting.getOutputFormat();
        this.resolver = setting.getContentResolver();
        this.mContext = setting.getApplicationContext();
        this.mFileName = setting.getOutputFileName();
    }

    //switch the current input channel to bluetooth mic
    public void startBluetoothMic(){
        if (mContext != null) {
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            mContext.getApplicationContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
                    Log.d(TAG, "Audio SCO state: " + state);
                    if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                        // now the connection has be established to the bluetooth device
                        mContext.getApplicationContext().unregisterReceiver(this);
                    }
                }
            }, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
            Log.d(TAG, "starting bluetooth");
            mAudioManager.startBluetoothSco();
        }
    }

    public MediaRecorder getmRecorder(){
        return mRecorder;
    }

    public MediaPlayer getmPlayer(){
        return mPlayer;
    }

    public int getAudioEncoder(){
        return audioEncoder;
    }

    public int getAudioSource(){
        return audioSource;
    }

    public int getOutputFormat(){
        return outputFormat;
    }

    public ContentResolver getResolver(){
        return resolver;
    }

    public Context getContext(){
        return mContext;
    }

    public String getFileName(){
        return mFileName;
    }
}
