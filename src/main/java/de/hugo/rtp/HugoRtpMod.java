package de.hugo.rtp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.lwjgl.glfw.GLFW;

public class HugoRtpMod implements ClientModInitializer {
    public static HugoRtpConfig config;
    public static HugoRtpMod instance;

    private static KeyBinding menuKey;
    private static KeyBinding toggleKey;

    private enum Phase { OFF, WAIT_TP, SETTLE, COOLDOWN }

    private Phase phase = Phase.OFF;
    private int wait = 0;
    private int elapsed = 0;
    private int attempts = 0;
    private double startX, startY, startZ;
    private String currentBiome = "-";

    @Override
    public void onInitializeClient() {
        instance = this;
        config = HugoRtpConfig.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("hugortp", "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hugortp.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, category));
        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hugortp.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, category));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register(this::renderHud);
    }

    public boolean isRunning() {
        return phase != Phase.OFF;
    }

    public void toggle(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        if (isRunning()) {
            stop(player, "RTP-Suche gestoppt.");
        } else {
            if (config.cleanCommand().isEmpty() || config.biomes == null || config.biomes.isBlank()) {
                player.sendMessage(Text.literal("[HugoRTP] Bitte erst Biom und Command im Menü (B) einstellen."), false);
                return;
            }
            attempts = 0;
            player.sendMessage(Text.literal("[HugoRTP] Suche gestartet: " + config.biomes), false);
            sendRtp(player);
        }
    }

    private void stop(ClientPlayerEntity player, String message) {
        phase = Phase.OFF;
        if (player != null && message != null) {
            player.sendMessage(Text.literal("[HugoRTP] " + message), false);
        }
    }

    private void sendRtp(ClientPlayerEntity player) {
        if (config.maxAttempts > 0 && attempts >= config.maxAttempts) {
            stop(player, "Maximale Versuche erreicht (" + config.maxAttempts + "). Gestoppt.");
            return;
        }
        attempts++;
        startX = player.getX();
        startY = player.getY();
        startZ = player.getZ();
        elapsed = 0;
        player.networkHandler.sendChatCommand(config.cleanCommand());
        phase = Phase.WAIT_TP;
    }

    private void onTick(MinecraftClient client) {
        while (menuKey.wasPressed()) {
            client.setScreen(new HugoRtpScreen(client.currentScreen));
        }
        while (toggleKey.wasPressed()) {
            toggle(client);
        }

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            phase = Phase.OFF;
            return;
        }
        if (phase == Phase.OFF) {
            return;
        }

        switch (phase) {
            case WAIT_TP -> {
                elapsed++;
                double dx = player.getX() - startX;
                double dy = player.getY() - startY;
                double dz = player.getZ() - startZ;
                if (dx * dx + dy * dy + dz * dz >= 100) {
                    // teleportiert -> kurz warten, bis Chunks/Biome geladen sind
                    phase = Phase.SETTLE;
                    wait = Math.max(1, config.settleSeconds * 20);
                } else if (elapsed >= Math.max(1, config.timeoutSeconds) * 20) {
                    // kein Teleport -> nach Pause nochmal versuchen
                    phase = Phase.COOLDOWN;
                    wait = Math.max(1, config.cooldownSeconds * 20);
                }
            }
            case SETTLE -> {
                if (wait-- > 0) {
                    return;
                }
                String[] id = biomeId(client, player);
                currentBiome = id == null ? "?" : id[1];
                if (id != null && config.matches(id[1], id[0])) {
                    player.sendMessage(Text.literal("[HugoRTP] Biom gefunden: " + id[1]
                            + " (nach " + attempts + " Versuchen)"), false);
                    phase = Phase.OFF;
                } else {
                    phase = Phase.COOLDOWN;
                    wait = Math.max(1, config.cooldownSeconds * 20);
                }
            }
            case COOLDOWN -> {
                if (wait-- <= 0) {
                    sendRtp(player);
                }
            }
            default -> phase = Phase.OFF;
        }
    }

    /** @return {voller Name, Pfad} z.B. {"minecraft:cherry_grove", "cherry_grove"} oder null */
    private static String[] biomeId(MinecraftClient client, ClientPlayerEntity player) {
        RegistryEntry<Biome> entry = client.world.getBiome(player.getBlockPos());
        Identifier id = entry.getKey().map(RegistryKey::getValue).orElse(null);
        if (id == null) {
            return null;
        }
        return new String[]{id.toString(), id.getPath()};
    }

    private void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.hudEnabled || client.player == null || client.options.hudHidden || phase == Phase.OFF) {
            return;
        }
        int x = config.hudX;
        int y = config.hudY;
        String max = config.maxAttempts > 0 ? "/" + config.maxAttempts : "";
        String status = switch (phase) {
            case WAIT_TP -> "warte auf Teleport...";
            case SETTLE -> "prüfe Biom...";
            case COOLDOWN -> "Pause...";
            default -> "";
        };
        context.drawTextWithShadow(client.textRenderer, "HugoRTP: " + status, x, y, 0xFFFFFFFF);
        context.drawTextWithShadow(client.textRenderer, "Ziel: " + config.biomes, x, y + 10, 0xFF55FF55);
        context.drawTextWithShadow(client.textRenderer, "Versuch: " + attempts + max, x, y + 20, 0xFFFFFF55);
        context.drawTextWithShadow(client.textRenderer, "Zuletzt: " + currentBiome, x, y + 30, 0xFFAAAAAA);
    }
}
