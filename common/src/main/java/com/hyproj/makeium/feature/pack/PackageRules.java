package com.hyproj.makeium.feature.pack;

import java.util.List;

/**
 * What a distributable map zip leaves out (paths relative to the world folder, forward slashes): player data and
 * statistics, the session lock, Makeium's own working files (checkpoints, block-coding projects) and backups.
 */
public final class PackageRules {
    private static final List<String> SKIP_PREFIX = List.of(
            "players/", "playerdata/", "stats/", "advancements/", "makeium/", "session.lock", "level.dat_old", "data/raids.dat"
    );

    private PackageRules() {}

    public static boolean skip(String relative) {
        String p = relative.replace('\\', '/');
        for (String s : SKIP_PREFIX) if (p.equals(s) || p.startsWith(s)) return true;
        if (p.startsWith("datapacks/") && p.contains("/makeium/")) return true;
        return p.endsWith(".mca_old") || p.endsWith("~") || p.endsWith(".tmp");
    }
}
