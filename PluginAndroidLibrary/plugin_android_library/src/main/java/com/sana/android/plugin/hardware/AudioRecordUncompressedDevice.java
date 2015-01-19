package com.sana.android.plugin.hardware;

import android.content.ContentResolver;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;
import com.sana.android.plugin.application.CommManager;
import com.sana.android.plugin.communication.MimeType;
import com.sana.android.plugin.data.BinaryDataWithPollingEvent;
import com.sana.android.plugin.data.DataWithEvent;
import com.sana.android.plugin.data.event.BytePollingDataEvent;
import org.apache.commons.io.IOUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;


/**
 * Created by Mia on 19/10/14.
 * This AudioRecordDevice class supports recording .3gp format files.
 */
public class AudioRecordUncompressedDevice implements GeneralDevice {
    private ContentResolver resolver;
    private String mFileName = "";
    private static final String LOG_TAG = "UncompressedAudioRecord";
    private static final String PREPARE_FAILED = "prepare() failed.";
    private static final String THREAD_NAME ="UncompressedAudioRecorder Thread";
    private static final int RECORDER_BPP = 16;
    private String AUDIO_RECORDER_FILE_EXT;
    private String AUDIO_RECORDER_FOLDER;
    private String AUDIO_RECORDER_TEMP_FILE;
    private int RECORDER_SAMPLERATE;
    private int RECORDER_CHANNELS;
    private int RECORDER_AUDIO_ENCODING;
    private int audioSource;
    private MediaPlayer   mPlayer = null;
    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    public AudioRecordUncompressedDevice(){
        mFileName=Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/audiorecordtest.wav";
    }

    private void ensureFileExists(String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
    }

    @Override
    public DataWithEvent prepare() {
        bufferSize = AudioRecord.getMinBufferSize(
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING);
        recorder = new AudioRecord(
                audioSource,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSize);
        try {
            ensureFileExists(mFileName);
            InputStream is = new FileInputStream(mFileName);
            DataWithEvent result = new BinaryDataWithPollingEvent(
                    Feature.MICROPHONE,
                    MimeType.AUDIO,
                    CommManager.getInstance().getUri(),
                    this,
                    is,
                    BytePollingDataEvent.BUFFER_SIZE_SMALL);
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
        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(new Runnable() {

            @Override
            public void run() {
                writeAudioDataToFile();
            }
        },THREAD_NAME);
        recordingThread.start();
    }
    @Override
    public void stop() {
        if(null != recorder){
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;

        }
        copyWaveFile(getTempFilename(),mFileName);
        deleteTempFile();
        moveData();
    }

    private void moveData() {
        try {
            Log.d(
                    "AudioRecordUncompressedDevice",
                    CommManager.getInstance().getUri().toString()
            );
            FileInputStream is = new FileInputStream(mFileName);
            OutputStream os = resolver.openOutputStream(CommManager.getInstance().getUri());
            Log.e(LOG_TAG, CommManager.getInstance().getUri().toString());
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
        file.delete();
    }


    @Override
    public void setCaptureSetting(CaptureSetting setting) {
        this.resolver = setting.getContentResolver();
        this.AUDIO_RECORDER_FILE_EXT = setting.getFileExtention();
        this.AUDIO_RECORDER_FOLDER = setting.getOutputFolderName();
        this.AUDIO_RECORDER_TEMP_FILE = setting.getTempFileName();
        this.RECORDER_SAMPLERATE = setting.getRecorderSampleRate();
        this.RECORDER_CHANNELS = setting.getRecorderChannels();
        this.RECORDER_AUDIO_ENCODING = setting.getAudioEncoder();
        this.audioSource = setting.getAudioSource();
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());
        file.delete();
    }
    private void writeAudioDataToFile(){
        byte data[] = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        int read = 0;

        if(null != os){
            while(isRecording){
                read = recorder.read(data, 0, bufferSize);
                if(AudioRecord.ERROR_INVALID_OPERATION != read){
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getTempFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            file.mkdirs();
        }

        File tempFile = new File(filepath,AUDIO_RECORDER_TEMP_FILE);

        if(tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }
    private void copyWaveFile(String inFilename,String outFilename){
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels/8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            Log.i(LOG_TAG,"File size: " + totalDataLen);

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while(in.read(data) != -1){
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }
    public void startPlaying() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            Log.e(LOG_TAG, PREPARE_FAILED);
        }
    }

    public void stopPlaying() {
        if (mPlayer.isPlaying())
            mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
    }

    public AudioRecord getRecorder(){
        return recorder;
    }

    public MediaPlayer getmPlayer(){
        return mPlayer;
    }
}
