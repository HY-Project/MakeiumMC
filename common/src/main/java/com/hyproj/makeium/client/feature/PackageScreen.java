package com.hyproj.makeium.client.feature;

import java.nio.file.Path;

import com.hyproj.makeium.client.Replies;
import com.hyproj.makeium.client.resourcepack.RpProject;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkField;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.client.ui.MkToggle;
import com.hyproj.makeium.feature.datapack.BlockCompiler;
import com.hyproj.makeium.net.Net;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * Map packaging: a pre-flight checklist (packs, spawn, difficulty, world resource pack) and one-click export of the
 * world as a distributable zip without player data or Makeium's working files.
 */
public class PackageScreen extends MkScreen {
    private static CompoundTag check = new CompoundTag();
    private static String lastPath = "";
    private static boolean bundleResources = true;
    private MkField name;

    public PackageScreen(Screen parent) {
        super(Component.translatable("makeium.feature.package"), parent);
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70;
        Replies.on("pkg/result", d -> {
            if (d.getBooleanOr("check", false)) {
                check = d;
                return;
            }
            if (d.getBooleanOr("ok", false)) {
                lastPath = d.getStringOr("path", "");
                MkToast.ok(Component.translatable("makeium.pkg.done", d.getLongOr("files", 0), String.format("%.1f MB", d.getLongOr("size", 0) / 1048576.0)));
                rebuildWidgets();
            } else {
                MkToast.error(Component.translatable("makeium.pkg.failed", d.getStringOr("error", "")));
            }
        });
        Net.sendToServer("pkg/check");
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        int colW = Math.min(260, width - 2 * PAD);
        int y = TOP + 26;
        String world = minecraft.getSingleplayerServer() != null ? minecraft.getSingleplayerServer().getWorldData().getLevelName() : "map";
        name = add(new MkField(PAD, y, colW, 18, Component.translatable("makeium.pkg.name")));
        name.value(BlockCompiler.sanitize(world));
        boolean local = minecraft.getSingleplayerServer() != null;
        if (local) add(new MkToggle(PAD, y + 24, colW, Component.translatable("makeium.pkg.bundle_rp"), bundleResources, v -> bundleResources = v));
        add(new MkButton(PAD, y + 48, colW, 22, Component.translatable("makeium.pkg.export"), MkButton.Kind.PRIMARY, this::export));
        if (!lastPath.isEmpty()) {
            add(new MkButton(PAD, y + 74, colW, 20, Component.translatable("makeium.pkg.open_folder"), MkButton.Kind.DEFAULT,
                    () -> com.mojang.blaze3d.Blaze3D.openPath(Path.of(lastPath).getParent())));
        }
    }

    private void export() {
        if (minecraft.getSingleplayerServer() != null && bundleResources && RpProject.current() != null) {
            try {
                RpProject.current().exportToWorld();
            } catch (Exception e) {
                MkToast.error(Component.translatable("makeium.rp.export_failed", e.getMessage()));
            }
        }
        CompoundTag d = new CompoundTag();
        d.putString("name", BlockCompiler.sanitize(name.getValue()));
        Net.sendToServer("pkg/export", d);
        MkToast.info(Component.translatable("makeium.pkg.working"));
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int colW = Math.min(260, width - 2 * PAD);
        MkTheme.caption(g, Component.translatable("makeium.pkg.name"), PAD, TOP + 12);
        int x = PAD * 2 + colW;
        if (x + 120 > width) x = PAD;
        int y = x == PAD ? TOP + 130 : TOP + 12;
        MkTheme.caption(g, Component.translatable("makeium.pkg.checklist"), x, y);
        y += 16;
        y = line(g, x, y, Component.translatable("makeium.pkg.world", check.getStringOr("world", "?")), true);
        y = line(g, x, y, Component.translatable("makeium.pkg.spawn", check.getStringOr("spawn", "?")), true);
        y = line(g, x, y, Component.translatable("makeium.pkg.difficulty", check.getStringOr("difficulty", "?")), true);
        StringBuilder packs = new StringBuilder();
        for (Tag t : check.getListOrEmpty("worldPacks")) packs.append(t.asString().orElse("")).append("  ");
        y = line(g, x, y, Component.translatable("makeium.pkg.datapacks", packs.isEmpty() ? "-" : packs.toString().strip()), !packs.isEmpty());
        boolean rp = check.getBooleanOr("resources", false) || bundleResources && RpProject.current() != null;
        y = line(g, x, y, Component.translatable(rp ? "makeium.pkg.rp_yes" : "makeium.pkg.rp_no"), rp);
        y = line(g, x, y, Component.translatable("makeium.pkg.excluded"), true);
        if (!lastPath.isEmpty()) {
            MkTheme.paragraph(g, Component.translatable("makeium.pkg.saved_to", lastPath), x, y + 6, width - x - PAD, MkTheme.GREEN_TEXT);
        }
    }

    private int line(GuiGraphicsExtractor g, int x, int y, Component c, boolean ok) {
        g.fill(x, y + 2, x + 6, y + 8, ok ? MkTheme.GREEN : MkTheme.YELLOW);
        return y + MkTheme.paragraph(g, c, x + 12, y, width - x - PAD - 12, MkTheme.TEXT) + 3;
    }
}
