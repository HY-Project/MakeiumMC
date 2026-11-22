package com.hyproj.makeium.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A clickable dashboard card: item icon, bold title, wrapped description; lifts (border turns green) on hover. */
public class MkCard extends AbstractButton {
    private final Component description;
    private final ItemStack icon;
    private final int accent;
    private final Runnable action;

    public MkCard(int x, int y, int w, int h, Component title, Component description, ItemStack icon, int accent, Runnable action) {
        super(x, y, w, h, title);
        this.description = description;
        this.icon = icon;
        this.accent = accent;
        this.action = action;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = isHoveredOrFocused();
        g.fill(x, y, x + w, y + h, hot ? MkTheme.SURFACE : MkTheme.PANEL);
        MkTheme.border(g, x, y, w, h, hot ? accent : MkTheme.LINE);
        g.fill(x, y, x + w, y + 2, accent);
        g.fill(x + 10, y + 12, x + 32, y + 34, MkTheme.withAlpha(accent, 0.25f));
        g.item(icon, x + 13, y + 15);
        MkTheme.boldText(g, MkTheme.clip(MkTheme.bold(), getMessage(), w - 50), x + 40, y + 14, MkTheme.TEXT);
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        MkTheme.paragraph(g, description, x + 40, y + 27, w - 50, MkTheme.MUTED);
        g.disableScissor();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
