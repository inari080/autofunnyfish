package com.inari.autofunnyfish;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class FunnyFishingAccessor {

    private static Field enabledField;
    private static Field lastActivityMsField;
    private static Method enableMethod;
    private static Object instance;

    static {
        try {
            Class<?> clazz =
                    Class.forName("jooon.features.fishing.FunnyFishing");

            enabledField = clazz.getDeclaredField("masterEnabled");
            enabledField.setAccessible(true);

            // 最後にキャスト／リールなどの釣り動作を行った時刻（釣れているかの目安）
            lastActivityMsField = clazz.getDeclaredField("lastActivityMs");
            lastActivityMsField.setAccessible(true);

            enableMethod =
                    clazz.getDeclaredMethod("enableFishing");
            enableMethod.setAccessible(true);

            Field instanceField =
                    clazz.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);

            instance = instanceField.get(null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isEnabled() {
        try {
            return enabledField.getBoolean(null);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 最後にキャスト／リールなどの釣り動作があった時刻（epoch ms）。
     * 取得できない場合は 0 を返す。
     */
    public static long getLastActivityMs() {
        try {
            return lastActivityMsField.getLong(null);
        } catch (Exception e) {
            return 0L;
        }
    }

    public static void enable() {
        try {
            enableMethod.invoke(instance);
        } catch (Exception ignored) {}
    }
}