package com.inari.autofunnyfish;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoFunnyFishingAddon implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("autofunnyfish", "main"));

    private static final Gson GSON = new Gson();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("autofunnyfish.json");

    // 実際に使うタイムアウト値（ms）。loadEnabled() の中で設定ファイルの値に上書きされる。
    // ※ autoRestartEnabled の初期化（loadEnabled呼び出し）より前に宣言しておく必要がある
    private static long noActivityTimeoutMs = 300_000L;

    // 自動再開機能のON/OFF（起動時に設定ファイルから読み込む）
    private static boolean autoRestartEnabled = loadEnabled();

    // 設定画面から変更可能なキー
    private static KeyMapping toggleKey;

    // 再試行間隔
    private long lastRetry = 0;

    // 直近で観測できた lastActivityMs（0 の間はまだ有効な値を観測していない扱い）
    private long lastSeenActivityMs = 0L;

    private static class Config {
        boolean autoRestartEnabled = true;
        // 何秒間キャスト/リールの動きが無ければ強制OFFにするか（0以下で無効）
        int noActivityTimeoutSeconds = 300;
    }

    private static boolean loadEnabled() {
        if (!Files.exists(CONFIG_PATH)) return true;

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            Config config = GSON.fromJson(reader, Config.class);
            if (config == null) return true;

            noActivityTimeoutMs = Math.max(0, config.noActivityTimeoutSeconds) * 1000L;
            return config.autoRestartEnabled;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return true;
        }
    }

    private static void saveEnabled() {
        Config config = new Config();
        config.autoRestartEnabled = autoRestartEnabled;
        config.noActivityTimeoutSeconds = (int) (noActivityTimeoutMs / 1000L);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ゲーム内チャットにメッセージを表示する（プレイヤーが存在する場合のみ）。
     */
    private static void sendChat(Minecraft client, String message, ChatFormatting color) {
        if (client == null || client.player == null) return;

        client.player.sendSystemMessage(
                Component.literal("[AutoFunnyFish] ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(message).withStyle(color))
        );
    }

    /**
     * FunnyFishing の lastActivityMs（最後にキャスト/リールした時刻）を監視し、
     * 一定時間ずっと動きが無ければ「釣れていない＝何か詰まっている」と判断して
     * autoRestartEnabled を強制OFFにする。
     */
    private void checkNoActivityTimeout(long now, Minecraft client) {
        if (noActivityTimeoutMs <= 0) return; // 0以下なら無効

        long activityMs = FunnyFishingAccessor.getLastActivityMs();
        if (activityMs <= 0) return; // まだ有効な値を取得できていない

        // 前回観測時と違う値なら「動きがあった」ので基準を更新
        if (activityMs != lastSeenActivityMs) {
            lastSeenActivityMs = activityMs;
            return;
        }

        // 値が変わらないまま一定時間経過 = 動きが止まっている
        if (now - lastSeenActivityMs >= noActivityTimeoutMs) {
            autoRestartEnabled = false;
            saveEnabled();

            String message = (noActivityTimeoutMs / 1000)
                    + "秒間釣りの動きが無かったため自動再開を強制OFFにしました";

            System.out.println("[AutoFunnyFish] " + message);
            sendChat(client, message, ChatFormatting.RED);
        }
    }

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
                saveEnabled();

                String message = "Auto restart " + (autoRestartEnabled ? "Enabled" : "Disabled");
                System.out.println("[AutoFunnyFish] " + message);
                sendChat(client, message, autoRestartEnabled ? ChatFormatting.GREEN : ChatFormatting.RED);
            }

            // 無効なら何もしない
            if (!autoRestartEnabled) return;

            // ワールド未参加
            if (client.player == null) {
                // ワールドを離れたら活動監視をリセット（次回参加時に誤検知しないように）
                lastSeenActivityMs = 0L;
                return;
            }

            long now = System.currentTimeMillis();

            // 一定時間キャスト/リールの動きが無ければ強制OFF
            checkNoActivityTimeout(now, client);
            if (!autoRestartEnabled) return;

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