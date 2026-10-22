package de.hugo.rtp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HugoRtpConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hugortp.json");

    /** Ziel-Biome, mit Komma getrennt. "jungle*" = Name enthaelt "jungle". */
    public String biomes = "cherry_grove";
    public String command = "rtp";
    public int cooldownSeconds = 3;
    public int settleSeconds = 2;
    public int timeoutSeconds = 15;
    /** 0 = unbegrenzt */
    public int maxAttempts = 100;
    public boolean hudEnabled = true;
    public int hudX = 4;
    public int hudY = 4;

    public static HugoRtpConfig load() {
        try {
            if (Files.exists(FILE)) {
                HugoRtpConfig cfg = GSON.fromJson(Files.readString(FILE), HugoRtpConfig.class);
                if (cfg != null) {
                    return cfg;
                }
            }
        } catch (Exception e) {
            System.err.println("[HugoRTP] Config konnte nicht geladen werden: " + e.getMessage());
        }
        HugoRtpConfig cfg = new HugoRtpConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[HugoRTP] Config konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public String cleanCommand() {
        String c = command == null ? "" : command.trim();
        while (c.startsWith("/")) {
            c = c.substring(1);
        }
        return c;
    }

    /** path = z.B. "cherry_grove", full = z.B. "minecraft:cherry_grove". */
    public boolean matches(String path, String full) {
        if (biomes == null) {
            return false;
        }
        for (String raw : biomes.split(",")) {
            String entry = raw.trim().toLowerCase().replace(' ', '_');
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.endsWith("*")) {
                String part = entry.substring(0, entry.length() - 1);
                if (!part.isEmpty() && path.contains(part)) {
                    return true;
                }
            } else if (entry.contains(":")) {
                if (full.equals(entry)) {
                    return true;
                }
            } else if (path.equals(entry)) {
                return true;
            }
        }
        return false;
    }
}
