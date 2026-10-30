package com.daytrace.ledger;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class CloudVoiceRecorder {
    private static final int SAMPLE_RATE = 16_000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int MAX_SECONDS = 55;
    private static final int MAX_PCM_BYTES =
            SAMPLE_RATE * MAX_SECONDS * (BITS_PER_SAMPLE / 8) * CHANNELS;

    private volatile AudioRecord audioRecord;
    private volatile boolean stopRequested;
    private volatile boolean cancelled;
    private final Context context;

    CloudVoiceRecorder(Context context) {
        this.context = context.getApplicationContext();
    }

    byte[] recordUntilStopped() throws IOException {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            throw new IOException("麦克风权限已关闭，请重新授权");
        }
        if (stopRequested) {
            throw new IOException("录音时间太短，请按住按钮说完后再松开");
        }
        int minimumBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimumBuffer <= 0) {
            throw new IOException("当前手机不支持 16 kHz 麦克风录音");
        }

        AudioRecord recorder = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(minimumBuffer * 2, 3_200))
                .build();
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            throw new IOException("麦克风初始化失败");
        }

        audioRecord = recorder;
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        byte[] buffer = new byte[3_200];
        try {
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IOException("麦克风未能开始录音");
            }

            while (!stopRequested && pcm.size() < MAX_PCM_BYTES) {
                int read = recorder.read(
                        buffer,
                        0,
                        Math.min(buffer.length, MAX_PCM_BYTES - pcm.size()),
                        AudioRecord.READ_BLOCKING);
                if (read > 0) {
                    pcm.write(buffer, 0, read);
                } else if (stopRequested || cancelled) {
                    break;
                } else if (read == AudioRecord.ERROR_DEAD_OBJECT) {
                    throw new IOException("麦克风录音会话已断开");
                } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                    throw new IOException("麦克风录音状态无效");
                }
            }
        } finally {
            try {
                recorder.stop();
            } catch (IllegalStateException ignored) {
                // The recorder may already have stopped after releasing the button.
            }
            recorder.release();
            audioRecord = null;
        }

        if (cancelled) throw new IOException("语音输入已取消");
        byte[] pcmBytes = pcm.toByteArray();
        if (pcmBytes.length < SAMPLE_RATE / 8) {
            throw new IOException("录音时间太短，请按住按钮说完后再松开");
        }
        return wav(pcmBytes);
    }

    void stop() {
        stopRequested = true;
        AudioRecord recorder = audioRecord;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (IllegalStateException ignored) {
                // The background thread is already closing the recorder.
            }
        }
    }

    void cancel() {
        cancelled = true;
        stop();
    }

    private byte[] wav(byte[] pcm) {
        int byteRate = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8);
        int blockAlign = CHANNELS * (BITS_PER_SAMPLE / 8);
        ByteArrayOutputStream output = new ByteArrayOutputStream(44 + pcm.length);
        writeAscii(output, "RIFF");
        writeIntLE(output, 36 + pcm.length);
        writeAscii(output, "WAVE");
        writeAscii(output, "fmt ");
        writeIntLE(output, 16);
        writeShortLE(output, 1);
        writeShortLE(output, CHANNELS);
        writeIntLE(output, SAMPLE_RATE);
        writeIntLE(output, byteRate);
        writeShortLE(output, blockAlign);
        writeShortLE(output, BITS_PER_SAMPLE);
        writeAscii(output, "data");
        writeIntLE(output, pcm.length);
        output.write(pcm, 0, pcm.length);
        return output.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream output, String value) {
        for (int index = 0; index < value.length(); index++) {
            output.write(value.charAt(index));
        }
    }

    private void writeIntLE(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private void writeShortLE(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }
}
