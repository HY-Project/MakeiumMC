package com.hyproj.makeium.client.feature;

import com.hyproj.makeium.client.tool.Selection;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkField;
import com.hyproj.makeium.client.ui.MkForm;
import com.hyproj.makeium.client.ui.MkNumberField;
import com.hyproj.makeium.client.ui.MkPicker;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.feature.edit.EditServer;
import com.hyproj.makeium.net.Net;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Region edit: fill, walls, replace, clear, copy/paste (rotate, mirror), move and undo on the two-corner selection
 * made with the selection tool (wooden axe: left click = corner A, right click = corner B).
 */
public class EditScreen extends MkScreen {
    private static String block = "minecraft:stone";
    private static String from = "minecraft:stone", to = "minecraft:air";
    private static int mx = 0, my = 1, mz = 0;
    private MkForm form;

    public EditScreen(Screen parent) {
        super(Component.translatable("makeium.feature.edit"), parent);
    }

    @Override
    protected ScreenRectangle scrollArea() {
        return new ScreenRectangle(0, TOP, width, height - TOP);
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70;
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        int colW = Math.min(300, (width - 3 * PAD) / 2);
        form = new MkForm(this, w -> addScrolled(w), PAD, TOP + 40 - scroll, colW);
        form.buttons(MkButton.Kind.DEFAULT,
                new Component[]{Component.translatable("makeium.tool.selection"), Component.translatable("makeium.edit.clear_selection")},
                new Runnable[]{() -> PackState.runCommand("makeium tool selection"), () -> {
                    Selection.clear();
                    rebuildWidgets();
                }});
        form.caption(Component.translatable("makeium.edit.fill_section"));
        MkField blockField = form.field(Component.translatable("makeium.edit.block"), block, s -> block = s);
        form.buttons(MkButton.Kind.DEFAULT,
                new Component[]{Component.translatable("makeium.slot.pick"), Component.translatable("makeium.slot.looked_block")},
                new Runnable[]{() -> openPopup(new MkPicker(this, Component.translatable("makeium.display.pick_block"), MkPicker.blocks(), id -> {
                    block = id.toString();
                    blockField.value(block);
                })), () -> {
                    if (minecraft.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && minecraft.level != null) {
                        block = blockStateString(minecraft.level.getBlockState(bh.getBlockPos()));
                        blockField.value(block);
                    }
                }});
        form.buttons(MkButton.Kind.PRIMARY,
                new Component[]{Component.translatable("makeium.edit.fill"), Component.translatable("makeium.edit.walls"), Component.translatable("makeium.edit.clear")},
                new Runnable[]{() -> op("fill", t -> t.putString("block", block)), () -> op("walls", t -> t.putString("block", block)), () -> op("clear", t -> {})});
        form.caption(Component.translatable("makeium.edit.replace_section"));
        form.field(Component.translatable("makeium.edit.from"), from, s -> from = s);
        form.field(Component.translatable("makeium.edit.to"), to, s -> to = s);
        form.button(Component.translatable("makeium.edit.replace"), MkButton.Kind.PRIMARY, () -> op("replace", t -> {
            t.putString("from", from);
            t.putString("to", to);
        }));

        int rx = PAD * 2 + colW;
        MkForm right = new MkForm(this, w -> addScrolled(w), rx, TOP + 40 - scroll, Math.min(300, width - rx - PAD - 4));
        right.caption(Component.translatable("makeium.edit.clipboard"));
        right.buttons(MkButton.Kind.DEFAULT,
                new Component[]{Component.translatable("makeium.edit.copy"), Component.translatable("makeium.edit.rotate"), Component.translatable("makeium.edit.mirror")},
                new Runnable[]{() -> op("copy", t -> {}), () -> raw("rotate"), () -> raw("mirror")});
        right.buttons(MkButton.Kind.PRIMARY,
                new Component[]{Component.translatable("makeium.edit.paste_here"), Component.translatable("makeium.edit.paste_a")},
                new Runnable[]{() -> raw("paste"), () -> {
                    if (Selection.a() == null) {
                        MkToast.error(Component.translatable("makeium.select.need"));
                        return;
                    }
                    BlockPos a = Selection.a();
                    CompoundTag d = new CompoundTag();
                    d.putString("op", "paste");
                    d.putIntArray("at", new int[]{a.getX(), a.getY(), a.getZ()});
                    Net.sendToServer("edit/op", d);
                }});
        right.caption(Component.translatable("makeium.edit.move_section"));
        int fw = (right.w - 8) / 3;
        MkNumberField fx = right.add(new MkNumberField(right.x, right.y, fw, 18, mx));
        MkNumberField fy = right.add(new MkNumberField(right.x + fw + 4, right.y, fw, 18, my));
        MkNumberField fz = right.add(new MkNumberField(right.x + 2 * (fw + 4), right.y, fw, 18, mz));
        fx.integer().onNumber(v -> mx = (int) v);
        fy.integer().onNumber(v -> my = (int) v);
        fz.integer().onNumber(v -> mz = (int) v);
        right.y += 22;
        right.button(Component.translatable("makeium.edit.move"), MkButton.Kind.PRIMARY, () -> op("move", t -> t.putIntArray("by", new int[]{mx, my, mz})));
        right.caption(Component.translatable("makeium.edit.history"));
        right.button(Component.translatable("makeium.edit.undo"), MkButton.Kind.DANGER, () -> raw("undo"));
    }

    private static String blockStateString(net.minecraft.world.level.block.state.BlockState s) {
        String str = s.toString();
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        int br = str.indexOf('[');
        return br >= 0 ? id + str.substring(br) : id;
    }

    private interface Args {
        void put(CompoundTag t);
    }

    private void op(String op, Args args) {
        if (!Selection.complete()) {
            MkToast.error(Component.translatable("makeium.select.need"));
            return;
        }
        if (Selection.volume() > EditServer.MAX_VOLUME) {
            MkToast.error(Component.translatable("makeium.edit.too_big", Selection.volume()));
            return;
        }
        CompoundTag d = Selection.toTag();
        d.putString("op", op);
        args.put(d);
        Net.sendToServer("edit/op", d);
    }

    private void raw(String op) {
        CompoundTag d = new CompoundTag();
        d.putString("op", op);
        Net.sendToServer("edit/op", d);
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        clipToScrollArea(g, () -> {
            int y = TOP + 10 - scroll;
            Component sel = Selection.complete()
                    ? Component.translatable("makeium.edit.selection", Selection.min().toShortString(), Selection.max().toShortString(), Selection.volume())
                    : Component.translatable("makeium.edit.no_selection");
            g.fill(PAD, y - 2, width - PAD, y + 22, MkTheme.SURFACE);
            g.fill(PAD, y - 2, PAD + 3, y + 22, Selection.complete() ? MkTheme.GREEN : MkTheme.YELLOW);
            MkTheme.text(g, MkTheme.clip(MkTheme.font(), sel, width - 3 * PAD), PAD + 10, y + 6, MkTheme.TEXT);
            form.draw(g);
        });
    }
}
