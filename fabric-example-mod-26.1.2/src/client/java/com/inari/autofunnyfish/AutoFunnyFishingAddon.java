package com.inari.autofunnyfish;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutoFunnyFishingAddon implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("autofunnyfish", "main"));

    // 自動再開機能のON/OFF
    private static boolean autoRestartEnabled = true;

    // 設定画面から変更可能なキー
    private static KeyMapping toggleKey;

    // 再試行間隔
    private long lastRetry = 0;

    @Override
    public void onInitializeClient() {

        // デフォルトは H キー
        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.autofunnyfish.toggle",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_H,
                        CATEGORY
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            // Hキーが押されたら切り替え
            while (toggleKey.consumeClick()) {
                autoRestartEnabled = !autoRestartEnabled;

                System.out.println(
                        "[AutoFunnyFish] Auto restart "
                                + (autoRestartEnabled ? "Enabled" : "Disabled")
                );
            }

            // 無効なら何もしない
            if (!autoRestartEnabled) return;

            // ワールド未参加
            if (client.player == null) return;

            long now = System.currentTimeMillis();

            // 5秒ごとにチェック
            if (now - lastRetry < 5000) return;

            // FunnyFishingが停止していたら再開
            if (!FunnyFishingAccessor.isEnabled()) {
                System.out.println("[AutoFunnyFish] Re-enabling FunnyFishing");

                FunnyFishingAccessor.enable();

                lastRetry = now;
            }
        });
    }
}