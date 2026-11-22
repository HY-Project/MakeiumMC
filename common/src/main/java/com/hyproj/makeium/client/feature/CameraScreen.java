package com.hyproj.makeium.client.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hyproj.makeium.client.Replies;
import com.hyproj.makeium.client.tool.CameraPath;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkDialog;
import com.hyproj.makeium.client.ui.MkField;
import com.hyproj.makeium.client.ui.MkList;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.feature.camera.CameraPack;
import com.hyproj.makeium.feature.datapack.BlockCompiler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * Camera cutscenes: record viewpoints with the camera tool (right click = add the current view), set how many ticks
 * each leg takes, then save & play. Saved cutscenes live in the "makeium_camera" pack.
 */
public class CameraScreen extends MkScreen {
    private static CompoundTag state = new CompoundTag();
    private boolean requested;
    private MkList keys;

    public CameraScreen(Screen parent) {
        super(Component.translatable("makeium.feature.camera"), parent);
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70 + 110;
        if (!requested) {
            requested = true;
            PackState.load(CameraPack.PACK, s -> {
                state = s;
                rebuildWidgets();
            });
        }
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        add(new MkButton(width - PAD - 60 - 6 - 104, 4, 104, 20, Component.translatable("makeium.ui.save_apply"), MkButton.Kind.PRIMARY, this::save));
        int colW = Math.min(200, (width - 3 * PAD) / 2);
        int y = TOP + 24;
        MkField name = add(new MkField(PAD, y, colW, 18, Component.translatable("makeium.camera.name")));
        name.value(CameraPath.name);
        name.onChange(s -> CameraPath.name = BlockCompiler.sanitizePath(s).replace('/', '_'));
        add(new MkButton(PAD, y + 24, colW, 20, Component.translatable("makeium.camera.add_view"), MkButton.Kind.PRIMARY, () -> {
            CameraPath.addCurrent();
            rebuildWidgets();
        }));
        add(new MkButton(PAD, y + 48, (colW - 4) / 2, 20, Component.translatable("makeium.camera.remove_last"), MkButton.Kind.DEFAULT, () -> {
            if (CameraPath.size() > 0) CameraPath.keys().remove(CameraPath.size() - 1);
            rebuildWidgets();
        }));
        add(new MkButton(PAD + (colW + 4) / 2, y + 48, (colW - 4) / 2, 20, Component.translatable("makeium.camera.clear"), MkButton.Kind.DANGER, () -> {
            CameraPath.clear();
            rebuildWidgets();
        }));
        add(new MkButton(PAD, y + 72, colW, 20, Component.translatable("makeium.camera.play"), MkButton.Kind.DEFAULT, () -> {
            save();
            PackState.runCommand("function " + CameraPack.NS + ":" + CameraPath.name + "/play");
            onClose();
        }));
        add(new MkButton(PAD, y + 96, colW, 20, Component.translatable("makeium.tool.camera"), MkButton.Kind.DEFAULT, () -> PackState.runCommand("makeium tool camera")));
        // saved cutscenes: click to load into the editor
        List<MkList.Item> saved = new ArrayList<>();
        for (Tag t : state.getListOrEmpty("cutscenes")) {
            if (!(t instanceof CompoundTag c)) continue;
            String n = c.getStringOr("name", "");
            saved.add(new MkList.Row(Component.literal(n), null, Component.literal(c.getListOrEmpty("keys").size() + ""), () -> {
                CameraPath.load(c);
                rebuildWidgets();
            }).selected(n.equals(CameraPath.name)));
        }
        if (!saved.isEmpty()) {
            MkList savedList = add(new MkList(PAD, y + 132, colW, Math.max(30, height - (y + 132) - PAD)));
            savedList.setItems(saved);
        }
        int rx = PAD * 2 + colW;
        keys = add(new MkList(rx, TOP + 24, width - rx - PAD, height - TOP - 24 - PAD));
        List<MkList.Item> rows = new ArrayList<>();
        List<CameraPath.Key> ks = CameraPath.keys();
        for (int i = 0; i < ks.size(); i++) {
            CameraPath.Key k = ks.get(i);
            final int idx = i;
            rows.add(new MkList.Row(Component.translatable("makeium.display.keyframe", i + 1),
                    Component.literal(String.format(Locale.ROOT, "%.1f %.1f %.1f · %.0f° %.0f°", k.x, k.y, k.z, k.yaw, k.pitch)),
                    Component.literal(i + 1 < ks.size() ? k.ticks + "t" : "—"), () ->
                    MkDialog.prompt(this, Component.translatable("makeium.camera.ticks"), Component.empty(), Integer.toString(k.ticks), v -> {
                        try {
                            ks.get(idx).ticks = Math.max(1, Integer.parseInt(v.strip()));
                        } catch (NumberFormatException ignored) {
                            // keep
                        }
                        rebuildWidgets();
                    })));
        }
        if (rows.isEmpty()) rows.add(new MkList.Note(Component.translatable("makeium.camera.empty"), MkTheme.FAINT));
        keys.setItems(rows);
    }

    private void save() {
        if (CameraPath.size() < 2) {
            MkToast.error(Component.translatable("makeium.display.need_two_keys"));
            return;
        }
        ListTag list = new ListTag();
        boolean replaced = false;
        for (Tag t : state.getListOrEmpty("cutscenes")) {
            if (t instanceof CompoundTag c && c.getStringOr("name", "").equals(CameraPath.name)) {
                list.add(CameraPath.save());
                replaced = true;
            } else {
                list.add(t);
            }
        }
        if (!replaced) list.add(CameraPath.save());
        state.put("cutscenes", list);
        List<CameraPack.Cutscene> scenes = new ArrayList<>();
        for (Tag t : list) {
            if (!(t instanceof CompoundTag c)) continue;
            List<CameraPack.Key> ks = new ArrayList<>();
            for (Tag kt : c.getListOrEmpty("keys")) {
                if (!(kt instanceof CompoundTag k)) continue;
                ks.add(new CameraPack.Key(k.getDoubleOr("x", 0), k.getDoubleOr("y", 0), k.getDoubleOr("z", 0), k.getFloatOr("yaw", 0), k.getFloatOr("pitch", 0), k.getIntOr("ticks", 40)));
            }
            scenes.add(new CameraPack.Cutscene(c.getStringOr("name", "intro"), ks));
        }
        PackState.save(CameraPack.PACK, "Makeium camera cutscenes", CameraPack.compile(scenes), state);
        MkToast.ok(Component.translatable("makeium.camera.saved", "/function " + CameraPack.NS + ":" + CameraPath.name + "/play"));
    }

    @Override
    public void removed() {
        Replies.off("dp/load");
        super.removed();
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int colW = Math.min(200, (width - 3 * PAD) / 2);
        MkTheme.caption(g, Component.translatable("makeium.camera.name"), PAD, TOP + 10);
        MkTheme.caption(g, Component.translatable("makeium.display.keyframes"), PAD * 2 + colW, TOP + 10);
        if (!state.getListOrEmpty("cutscenes").isEmpty()) MkTheme.caption(g, Component.translatable("makeium.camera.saved_list"), PAD, TOP + 24 + 120);
    }
}
