package com.daytrace.ledger;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class CloudSpeechSettingsStore {
    static final String DEFAULT_ENGINE = "16k_zh";

    private static final String PREFERENCES = "daytrace_cloud_speech";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CONSENT = "consent";
    private static final String KEY_ENGINE = "engine";
    private static final String KEY_SECRET_ID = "secret_id_encrypted";
    private static final String KEY_SECRET_KEY = "secret_key_encrypted";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "daytrace_tencent_voice_credentials";

    private final SharedPreferences preferences;

    CloudSpeechSettingsStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    Settings load() {
        return new Settings(
                preferences.getBoolean(KEY_ENABLED, false),
                preferences.getBoolean(KEY_CONSENT, false),
                preferences.getString(KEY_ENGINE, DEFAULT_ENGINE),
                decrypt(preferences.getString(KEY_SECRET_ID, "")),
                decrypt(preferences.getString(KEY_SECRET_KEY, "")));
    }

    JSONObject toUiJson() {
        Settings settings = load();
        try {
            JSONObject value = new JSONObject();
            value.put("enabled", settings.enabled);
            value.put("consent", settings.consent);
            value.put("engine", settings.engine);
            value.put("hasSecretId", !settings.secretId.isEmpty());
            value.put("hasSecretKey", !settings.secretKey.isEmpty());
            return value;
        } catch (JSONException error) {
            return new JSONObject();
        }
    }

    JSONObject saveFromJson(String payload) {
        try {
            JSONObject input = new JSONObject(payload);
            boolean enabled = input.optBoolean("enabled", false);
            boolean consent = input.optBoolean("consent", false);
            String engine = input.optString("engine", DEFAULT_ENGINE).trim();
            String providedSecretId = input.optString("secretId", "").trim();
            String providedSecretKey = input.optString("secretKey", "").trim();

            if (!isSupportedEngine(engine)) {
                return result(false, "请选择有效的腾讯云识别引擎");
            }
            if (enabled && !consent) {
                return result(false, "启用前需要确认录音会发送到腾讯云");
            }

            String encryptedSecretId = preferences.getString(KEY_SECRET_ID, "");
            String encryptedSecretKey = preferences.getString(KEY_SECRET_KEY, "");
            if (!providedSecretId.isEmpty()) {
                encryptedSecretId = encrypt(providedSecretId);
                if (encryptedSecretId.isEmpty()) {
                    return result(false, "SecretId 加密保存失败");
                }
            }
            if (!providedSecretKey.isEmpty()) {
                encryptedSecretKey = encrypt(providedSecretKey);
                if (encryptedSecretKey.isEmpty()) {
                    return result(false, "SecretKey 加密保存失败");
                }
            }
            if (enabled && (encryptedSecretId.isEmpty() || encryptedSecretKey.isEmpty())) {
                return result(false, "请填写 SecretId 和 SecretKey 后再启用");
            }

            preferences.edit()
                    .putBoolean(KEY_ENABLED, enabled)
                    .putBoolean(KEY_CONSENT, consent)
                    .putString(KEY_ENGINE, engine)
                    .putString(KEY_SECRET_ID, encryptedSecretId)
                    .putString(KEY_SECRET_KEY, encryptedSecretKey)
                    .apply();
            return result(true, enabled
                    ? "腾讯云语音记账已启用"
                    : "腾讯云语音记账已关闭");
        } catch (JSONException error) {
            return result(false, "语音设置格式无效");
        }
    }

    private boolean isSupportedEngine(String engine) {
        return DEFAULT_ENGINE.equals(engine)
                || "16k_zh-PY".equals(engine)
                || "16k_zh_dialect".equals(engine)
                || "16k_yue".equals(engine);
    }

    private JSONObject result(boolean ok, String message) {
        try {
            JSONObject value = new JSONObject();
            value.put("ok", ok);
            value.put("message", message);
            value.put("settings", toUiJson());
            return value;
        } catch (JSONException error) {
            return new JSONObject();
        }
    }

    private String encrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            byte[] combined = Base64.decode(encoded, Base64.NO_WRAP);
            if (combined.length <= 12) return "";
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    static final class Settings {
        final boolean enabled;
        final boolean consent;
        final String engine;
        final String secretId;
        final String secretKey;

        Settings(
                boolean enabled,
                boolean consent,
                String engine,
                String secretId,
                String secretKey) {
            this.enabled = enabled;
            this.consent = consent;
            this.engine = engine;
            this.secretId = secretId;
            this.secretKey = secretKey;
        }

        boolean canRecognize() {
            return enabled && consent
                    && !secretId.isEmpty()
                    && !secretKey.isEmpty()
                    && !engine.isEmpty();
        }
    }
}
