package com.lzqlove.jiaowu.keepalive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 保活可观测字段在 SharedPreferences 里的写入、读取与清理语义。
 *
 * 本地单测（app/src/test）是纯 JVM，没有 Robolectric 也没有真实 Context，
 * 无法直接 new 出 SharedPreferences；这里用内存实现跑 Store 的包内重载，
 * 覆盖的仍是真实的键名、默认值和 clear 行为，不引入新依赖。
 */
public class SessionKeepAliveStoreTest {

    // 键名固定写在这里：Store 改名或换存储位置时测试要能立刻报警
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_CHECK_AT = "lastCheckAt";
    private static final String KEY_LAST_RESULT = "lastResult";
    private static final String KEY_NEXT_CHECK_AT = "nextCheckAt";

    private final FakePreferences prefs = new FakePreferences();

    @Test
    public void recordsLastCheckTimeAndResult() {
        SessionKeepAliveStore.recordCheckResult(prefs, 1762056000000L, "ALIVE");
        assertEquals(1762056000000L, SessionKeepAliveStore.getLastCheckAt(prefs));
        assertEquals("ALIVE", SessionKeepAliveStore.getLastResult(prefs));
        // 同一字段重复记录时以最新一次为准
        SessionKeepAliveStore.recordCheckResult(prefs, 1762057800000L, "EXPIRED");
        assertEquals(1762057800000L, SessionKeepAliveStore.getLastCheckAt(prefs));
        assertEquals("EXPIRED", SessionKeepAliveStore.getLastResult(prefs));
    }

    @Test
    public void missingValuesReadAsZeroAndNull() {
        assertEquals(0L, SessionKeepAliveStore.getLastCheckAt(prefs));
        assertNull(SessionKeepAliveStore.getLastResult(prefs));
        assertEquals(0L, SessionKeepAliveStore.getNextCheckAt(prefs));
    }

    @Test
    public void nextCheckAtIsStoredAndCleared() {
        long triggerAt = 1762057800000L;
        SessionKeepAliveStore.setNextCheckAt(prefs, triggerAt);
        assertEquals(triggerAt, SessionKeepAliveStore.getNextCheckAt(prefs));
        assertTrue(prefs.contains(KEY_NEXT_CHECK_AT));

        // 取消排程传 0：键直接移除，读取回到 0（调用方据此返回 null）
        SessionKeepAliveStore.setNextCheckAt(prefs, 0L);
        assertEquals(0L, SessionKeepAliveStore.getNextCheckAt(prefs));
        assertFalse(prefs.contains(KEY_NEXT_CHECK_AT));
    }

    @Test
    public void clearRemovesObservabilityButKeepsEnabledSwitch() {
        SessionKeepAliveStore.setEnabled(prefs, true);
        SessionKeepAliveStore.recordCheckResult(prefs, 1762056000000L, "FAILED");
        SessionKeepAliveStore.setNextCheckAt(prefs, 1762057800000L);

        SessionKeepAliveStore.clear(prefs);

        // 明确退出登录只清历史，开关状态由 setKeepAlive(false) 自己负责，避免语义互相覆盖
        assertTrue(SessionKeepAliveStore.isEnabled(prefs));
        assertEquals(0L, SessionKeepAliveStore.getLastCheckAt(prefs));
        assertNull(SessionKeepAliveStore.getLastResult(prefs));
        assertEquals(0L, SessionKeepAliveStore.getNextCheckAt(prefs));
        assertFalse(prefs.contains(KEY_LAST_CHECK_AT));
        assertFalse(prefs.contains(KEY_LAST_RESULT));
        assertFalse(prefs.contains(KEY_NEXT_CHECK_AT));
    }

    @Test
    public void unknownOrEmptyResultReadsAsNull() {
        prefs.edit().putString(KEY_LAST_RESULT, "WHATEVER").apply();
        assertNull(SessionKeepAliveStore.getLastResult(prefs));

        prefs.edit().putString(KEY_LAST_RESULT, "").apply();
        assertNull(SessionKeepAliveStore.getLastResult(prefs));
    }

    /** 内存版 SharedPreferences：只实现本测试用到的读写语义，commit/apply 立即生效。 */
    private static final class FakePreferences implements SharedPreferences {

        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(values);
        }

        @Override
        public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object value = values.get(key);
            return value instanceof Set ? new HashSet<>((Set<String>) value) : defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new FakeEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            // 测试不关心变更回调
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            // 测试不关心变更回调
        }

        private final class FakeEditor implements Editor {

            private final Map<String, Object> pending = new HashMap<>(values);

            @Override
            public Editor putString(String key, String value) {
                if (value == null) {
                    pending.remove(key);
                } else {
                    pending.put(key, value);
                }
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> value) {
                if (value == null) {
                    pending.remove(key);
                } else {
                    pending.put(key, new HashSet<>(value));
                }
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                pending.remove(key);
                return this;
            }

            @Override
            public Editor clear() {
                pending.clear();
                return this;
            }

            @Override
            public boolean commit() {
                applyValues();
                return true;
            }

            @Override
            public void apply() {
                applyValues();
            }

            private void applyValues() {
                values.clear();
                values.putAll(pending);
            }
        }
    }
}
