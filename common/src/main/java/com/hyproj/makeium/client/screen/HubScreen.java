package com.hyproj.makeium.client.screen;

import java.util.List;

import com.hyproj.makeium.client.MakeiumClient;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkCard;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.server.Tools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Makeium dashboard: one card per factory/tool, and a row of tool buttons. Scrolls on small windows. */
public class HubScreen extends MkScreen {
    private record Entry(String screen, ItemStack icon, int accent) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("display", new ItemStack(Items.OAK_SIGN), 0xFF52A535),
            new Entry("resourcepack", new ItemStack(Items.PAINTING), 0xFF4A90D9),
            new Entry("datapack", new ItemStack(Items.COMMAND_BLOCK), 0xFFE8B931),
            new Entry("regions", new ItemStack(Items.EMERALD), 0xFF3FBF8F),
            new Entry("edit", new ItemStack(Items.WOODEN_AXE), 0xFFC07A3A),
            new Entry("npc", new ItemStack(Items.NAME_TAG), 0xFFB36AD9),
            new Entry("camera", new ItemStack(Items.SPYGLASS), 0xFFD96A8F),
            new Entry("scoreboard", new ItemStack(Items.WRITABLE_BOOK), 0xFF6A8FD9),
            new Entry("checkpoint", new ItemStack(Items.RECOVERY_COMPASS), 0xFF8FD96A),
            new Entry("package", new ItemStack(Items.CHEST), 0xFFD9B36A)
    );
    private int cardH = 52;
    private int toolsY;

    public HubScreen(Screen parent) {
        super(Component.translatable("makeium.hub.title"), parent);
    }

    @Override
    protected ScreenRectangle scrollArea() {
        return new ScreenRectangle(0, TOP, width, height - TOP);
    }

    private int cols() {
        return Math.max(2, Math.min(4, (width - 2 * PAD) / 150));
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70;
        int cols = cols();
        int cardW = (width - 2 * PAD - (cols - 1) * 6) / cols;
        int lines = 1;
        for (Entry e : ENTRIES) lines = Math.max(lines, MkTheme.font().split(Component.translatable("makeium.feature." + e.screen + ".desc"), cardW - 50).size());
        cardH = 32 + lines * 11;
        int top = TOP + 34 - scroll;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry e = ENTRIES.get(i);
            int x = PAD + (i % cols) * (cardW + 6);
            int y = top + (i / cols) * (cardH + 6);
            addScrolled(new MkCard(x, y, cardW, cardH, Component.translatable("makeium.feature." + e.screen),
                    Component.translatable("makeium.feature." + e.screen + ".desc"), e.icon, e.accent, () -> MakeiumClient.openScreen(e.screen)));
        }
        int rows = (ENTRIES.size() + cols - 1) / cols;
        toolsY = top + rows * (cardH + 6) + 8;
        int ty = toolsY + 14;
        int tx = PAD;
        for (Tools t : Tools.values()) {
            Component label = Component.translatable("makeium.tool." + t.id());
            int w = MkTheme.font().width(label) + 16;
            if (tx + w > width - PAD) {
                tx = PAD;
                ty += 24;
            }
            addScrolled(new MkButton(tx, ty, w, 20, label, MkButton.Kind.DEFAULT, () -> giveTool(t)));
            tx += w + 6;
        }
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
    }

    private void giveTool(Tools t) {
        if (!MakeiumClient.serverHasMakeium()) {
            MkToast.error(Component.translatable("makeium.error.server_missing"));
            return;
        }
        CompoundTag d = new CompoundTag();
        d.putString("command", "makeium tool " + t.id());
        Net.sendToServer("cmd/run", d);
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        clipToScrollArea(g, () -> {
            int y = TOP + 8 - scroll;
            MkTheme.boldText(g, Component.translatable("makeium.hub.heading"), PAD, y, MkTheme.TEXT);
            MkTheme.text(g, MkTheme.clip(MkTheme.font(), Component.translatable("makeium.hub.sub"), width - 2 * PAD), PAD, y + 12, MkTheme.MUTED);
            MkTheme.caption(g, Component.translatable("makeium.hub.tools"), PAD, toolsY);
        });
        if (!MakeiumClient.serverHasMakeium()) {
            g.fill(0, height - 14, width, height, 0xE0302408);
            MkTheme.text(g, Component.translatable("makeium.error.server_missing"), PAD, height - 11, MkTheme.YELLOW);
        } else if (!MakeiumClient.canEdit()) {
            g.fill(0, height - 14, width, height, 0xE0302408);
            MkTheme.text(g, Component.translatable("makeium.error.no_permission"), PAD, height - 11, MkTheme.YELLOW);
        }
    }
}
