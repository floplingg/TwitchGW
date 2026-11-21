package dev.flop.giveaway;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Saved to .minecraft/config/flopgiveaway.json */
public final class Config {
    public static final int[] ACCENTS = {
            0xFF7AB8FF, // light blue
            0xFFFFB454, // amber
            0xFFB98CFF, // purple
            0xFF5BE49B, // mint
            0xFFFF6B8A, // pink
            0xFFF2F2F2  // white
    };

    public String twitchChannel = "";
    public String streamlabsToken = "";
    public String prize = "25M";
    public int durationSec = 300;
    public int winners = 1;
    public boolean subsOnly = false;
    public boolean donationEntries = true;
    public boolean showHud = true;
    public int accent = ACCENTS[0];

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public static Config get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("flopgiveaway.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            FlopGiveaway.LOG.warn("Could not read config, using defaults", e);
        }
        instance.clamp();
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (Exception e) {
            FlopGiveaway.LOG.warn("Could not save config", e);
        }
    }

    void clamp() {
        if (twitchChannel == null) twitchChannel = "";
        if (streamlabsToken == null) streamlabsToken = "";
        if (prize == null) prize = "";
        durationSec = Math.max(30, Math.min(3600, durationSec));
        winners = Math.max(1, Math.min(20, winners));
        if ((accent >>> 24) == 0) accent |= 0xFF000000;
    }
}
