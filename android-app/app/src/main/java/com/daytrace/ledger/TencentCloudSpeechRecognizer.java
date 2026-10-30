package com.daytrace.ledger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class TencentCloudSpeechRecognizer {
    private static final String HOST = "asr.tencentcloudapi.com";
    private static final String SERVICE = "asr";
    private static final String ACTION = "SentenceRecognition";
    private static final String VERSION = "2019-06-14";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String FINANCE_HOTWORDS =
            "记账|6,收入|6,支出|6,消费|5,转账|5,工资|5,午餐|5,晚餐|5,打车|5";

    private TencentCloudSpeechRecognizer() {}

    static String recognize(byte[] wav, CloudSpeechSettingsStore.Settings settings)
            throws IOException {
        if (wav == null || wav.length == 0) {
            throw new IOException("录音数据为空");
        }

        String requestBody;
        try {
            JSONObject request = new JSONObject();
            request.put("EngSerViceType", settings.engine);
            request.put("SourceType", 1);
            request.put("VoiceFormat", "wav");
            request.put("Data", Base64.getEncoder().encodeToString(wav));
            request.put("DataLen", wav.length);
            request.put("FilterDirty", 0);
            request.put("FilterModal", 1);
            request.put("FilterPunc", 0);
            request.put("ConvertNumMode", 1);
            request.put("WordInfo", 0);
            request.put("HotwordList", FINANCE_HOTWORDS);
            requestBody = request.toString();
        } catch (JSONException error) {
            throw new IOException("无法生成语音识别请求", error);
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        String authorization = authorization(
                requestBody,
                timestamp,
                settings.secretId,
                settings.secretKey);

        HttpURLConnection connection = (HttpURLConnection)
                new URL("https://" + HOST + "/").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(45_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", CONTENT_TYPE);
        connection.setRequestProperty("X-TC-Action", ACTION);
        connection.setRequestProperty("X-TC-Version", VERSION);
        connection.setRequestProperty("X-TC-Timestamp", Long.toString(timestamp));
        connection.setRequestProperty("Authorization", authorization);

        byte[] body = requestBody.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }

        int status = connection.getResponseCode();
        InputStream responseStream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String responseBody = responseStream == null
                ? ""
                : readAll(responseStream);
        connection.disconnect();

        if (responseBody.isEmpty()) {
            throw new IOException("腾讯云语音识别没有返回内容，HTTP " + status);
        }
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONObject response = root.optJSONObject("Response");
            if (response == null) {
                throw new IOException("腾讯云语音识别响应格式异常");
            }
            JSONObject error = response.optJSONObject("Error");
            if (error != null) {
                String code = error.optString("Code", "UnknownError");
                String message = error.optString("Message", "未知错误");
                String requestId = response.optString("RequestId", "");
                throw new IOException(
                        "腾讯云语音识别失败：" + code + " " + message
                                + (requestId.isEmpty() ? "" : "（" + requestId + "）"));
            }
            String result = response.optString("Result", "").trim();
            if (result.isEmpty()) {
                throw new IOException("腾讯云没有识别出文字，请靠近麦克风重试");
            }
            return result;
        } catch (JSONException error) {
            throw new IOException("腾讯云语音识别响应无法解析，HTTP " + status, error);
        }
    }

    private static String authorization(
            String requestBody,
            long timestamp,
            String secretId,
            String secretKey) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = dateFormat.format(new Date(timestamp * 1000L));

        String canonicalHeaders =
                "content-type:" + CONTENT_TYPE + "\n"
                        + "host:" + HOST + "\n"
                        + "x-tc-action:" + ACTION.toLowerCase(Locale.ROOT) + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String canonicalRequest =
                "POST\n"
                        + "/\n"
                        + "\n"
                        + canonicalHeaders + "\n"
                        + signedHeaders + "\n"
                        + sha256Hex(requestBody);

        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign =
                ALGORITHM + "\n"
                        + timestamp + "\n"
                        + credentialScope + "\n"
                        + sha256Hex(canonicalRequest);

        byte[] secretDate = hmacSha256(
                ("TC3" + secretKey).getBytes(StandardCharsets.UTF_8),
                date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = hex(hmacSha256(secretSigning, stringToSign));

        return ALGORITHM + " Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private static String sha256Hex(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IOException("无法计算语音请求摘要", error);
        }
    }

    private static byte[] hmacSha256(byte[] key, String value) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IOException("无法签发语音请求", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static String readAll(InputStream input) throws IOException {
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4_096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
