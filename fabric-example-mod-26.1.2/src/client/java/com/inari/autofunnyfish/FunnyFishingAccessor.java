package com.inari.autofunnyfish;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class FunnyFishingAccessor {

    private static Field enabledField;
    private static Method enableMethod;
    private static Object instance;

    static {
        try {
            Class<?> clazz =
                    Class.forName("jooon.features.fishing.FunnyFishing");

            enabledField = clazz.getDeclaredField("masterEnabled");
            enabledField.setAccessible(true);

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

    public static void enable() {
        try {
            enableMethod.invoke(instance);
        } catch (Exception ignored) {}
    }
}