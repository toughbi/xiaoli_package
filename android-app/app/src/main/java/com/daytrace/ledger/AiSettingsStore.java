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

final class AiSettingsStore {
    static final String MODE_HYBRID = "hybrid";
    static final String MODE_ALWAYS = "always";
    static final String MODE_LOCAL = "local";

    static final String DEFAULT_ENDPOINT =
            "https://api.deepseek.com/v1/chat/completions";
    static final String DEFAULT_MODEL = "deepseek-chat";

    private static final String PREFERENCES = "daytrace_ai_settings";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SETTINGS_VERSION = "settings_version";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_MODEL = "model";
    private static final String KEY_MODE = "mode";
    private static final String KEY_API_KEY = "api_key_encrypted";
    private static final String KEY_CONSENT = "cloud_consent";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "daytrace_ai_api_key";

    private final SharedPreferences preferences;

    private static final int SETTINGS_VERSION = 2;

    AiSettingsStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    Settings load() {
        migrateIfNeeded();
        String mode = preferences.getString(KEY_MODE, MODE_HYBRID);
        if (MODE_LOCAL.equals(mode)) mode = MODE_HYBRID;
        return new Settings(
                preferences.getBoolean(KEY_ENABLED, false),
                preferences.getString(KEY_PROVIDER, "deepseek"),
                preferences.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT),
                preferences.getString(KEY_MODEL, DEFAULT_MODEL),
                mode,
                decrypt(preferences.getString(KEY_API_KEY, "")),
                preferences.getBoolean(KEY_CONSENT, false));
    }

    private void migrateIfNeeded() {
        int version = preferences.getInt(KEY_SETTINGS_VERSION, 0);
        if (version >= SETTINGS_VERSION) return;
        String provider = preferences.getString(KEY_PROVIDER, "deepseek");
        String endpoint = preferences.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT);
        boolean previousNonDeepSeek = !"deepseek".equals(provider)
                || endpoint.contains("siliconflow")
                || endpoint.contains("dashscope");
        SharedPreferences.Editor editor = preferences.edit()
                .putInt(KEY_SETTINGS_VERSION, SETTINGS_VERSION)
                .putString(KEY_PROVIDER, "deepseek");
        if (previousNonDeepSeek) {
            editor.putBoolean(KEY_ENABLED, false)
                    .putBoolean(KEY_CONSENT, false)
                    .putString(KEY_ENDPOINT, DEFAULT_ENDPOINT)
                    .putString(KEY_MODEL, DEFAULT_MODEL)
                    .remove(KEY_API_KEY);
        }
        editor.apply();
    }

    JSONObject toUiJson() {
        Settings settings = load();
        try {
            JSONObject value = new JSONObject();
            value.put("enabled", settings.enabled);
            value.put("provider", settings.provider);
            value.put("endpoint", settings.endpoint);
            value.put("model", settings.model);
            value.put("mode", settings.mode);
            value.put("hasApiKey", !settings.apiKey.isEmpty());
            value.put("consent", settings.consent);
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
            String provider = input.optString("provider", "deepseek").trim();
            String endpoint = input.optString("endpoint", DEFAULT_ENDPOINT).trim();
            String model = input.optString("model", DEFAULT_MODEL).trim();
            String mode = input.optString("mode", MODE_HYBRID);
            String providedKey = input.optString("apiKey", "").trim();

            if (endpoint.isEmpty() || !endpoint.startsWith("https://")) {
                return result(false, "接口地址必须使用 HTTPS");
            }
            if (model.isEmpty()) return result(false, "请填写模型名称");
            if (MODE_LOCAL.equals(mode)) mode = MODE_HYBRID;
            if (!MODE_HYBRID.equals(mode)
                    && !MODE_ALWAYS.equals(mode)
                    && !MODE_LOCAL.equals(mode)) {
                return result(false, "识别模式无效");
            }
            if (enabled && !consent) {
                return result(false, "启用前需要确认通知正文会发送到模型服务");
            }

            String encryptedKey = preferences.getString(KEY_API_KEY, "");
            if (!providedKey.isEmpty()) {
                encryptedKey = encrypt(providedKey);
                if (encryptedKey.isEmpty()) return result(false, "API Key 加密保存失败");
            }
            if (enabled && encryptedKey.isEmpty()) {
                return result(false, "请填写 API Key 后再启用");
            }

            preferences.edit()
                    .putBoolean(KEY_ENABLED, enabled)
                    .putString(KEY_PROVIDER, provider)
                    .putBoolean(KEY_CONSENT, consent)
                    .putString(KEY_ENDPOINT, endpoint)
                    .putString(KEY_MODEL, model)
                    .putString(KEY_MODE, mode)
                    .putString(KEY_API_KEY, encryptedKey)
                    .apply();
            return result(true, enabled ? "AI 财务专家已开启" : "AI 财务专家已关闭");
        } catch (JSONException error) {
            return result(false, "设置格式无效");
        }
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
        final String provider;
        final String endpoint;
        final String model;
        final String mode;
        final String apiKey;
        final boolean consent;

        Settings(
                boolean enabled,
                String provider,
                String endpoint,
                String model,
                String mode,
                String apiKey,
                boolean consent) {
            this.enabled = enabled;
            this.provider = provider;
            this.endpoint = endpoint;
            this.model = model;
            this.mode = mode;
            this.apiKey = apiKey;
            this.consent = consent;
        }

        boolean canCallModel() {
            return enabled && consent && !apiKey.isEmpty() && endpoint.startsWith("https://");
        }
    }
}
