package com.hyproj.makeium.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Web-app frame for every Makeium screen: a 28px top bar (brand, page title, actions on the right), an optional left
 * sidebar for navigation, and the content area. Popups (dropdowns, colour pickers, dialogs) float above everything and
 * receive input first. Toasts appear bottom-right.
 *
 * <p>Long pages scroll: widgets added with {@link #addScrolled} are clipped to {@link #scrollArea()}, the page is laid
 * out again with {@link #scroll} subtracted when the wheel turns over that area (Minecraft's default GUI scale leaves
 * only about 427×240 GUI pixels, so most forms need it).
 */
public abstract class MkScreen extends Screen {
    public static final int TOP = 28;
    public static final int PAD = 10;
    /** Minecraft's diamond pickaxe recoloured green (branding/gen_logo.py). */
    public static final net.minecraft.resources.Identifier LOGO = com.hyproj.makeium.Makeium.id("textures/gui/logo.png");

    protected final Screen parent;
    private final List<MkPopup> popups = new ArrayList<>();
    private final List<AbstractWidget> scrolled = new ArrayList<>();
    /** Current scroll offset of the scroll area (GUI px). */
    protected int scroll;
    /** Space the top-right actions take (the title is clipped before it). */
    protected int topActionsWidth = 90;

    protected MkScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    public Screen parent() {
        return parent;
    }

    /** Sidebar width (0 = none). */
    protected int sidebarWidth() {
        return 0;
    }

    /** Draw the world dimmed behind instead of the solid background. */
    protected boolean seeThrough() {
        return false;
    }

    /** The scrolling part of the page, or null when nothing scrolls. */
    protected @Nullable ScreenRectangle scrollArea() {
        return null;
    }

    @Override
    protected void init() {
        popups.clear();
        scrolled.clear();
    }

    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        scrolled.clear();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- scrolling

    /** Adds a widget that lives in the scroll area (position it with {@link #scroll} already subtracted). */
    public <T extends AbstractWidget> T addScrolled(T w) {
        addRenderableWidget(w);
        scrolled.add(w);
        return w;
    }

    private int maxScroll() {
        ScreenRectangle area = scrollArea();
        if (area == null) return 0;
        int bottom = extraContentBottom();
        for (AbstractWidget w : scrolled) bottom = Math.max(bottom, w.getY() + w.getHeight());
        return Math.max(0, bottom + scroll + 8 - area.bottom());
    }

    /** Bottom (with the current scroll applied) of content drawn without widgets, e.g. a help paragraph. */
    protected int extraContentBottom() {
        return 0;
    }

    /** Re-lays the page after scrolling; screens that build pages themselves override this. */
    protected void onScrolled() {
        rebuildWidgets();
    }

    private boolean inScrollArea(double mx, double my) {
        ScreenRectangle a = scrollArea();
        return a != null && mx >= a.left() && mx < a.right() && my >= a.top() && my < a.bottom();
    }

    /** Runs {@code r} clipped to the scroll area (labels drawn next to scrolled widgets). */
    protected void clipToScrollArea(GuiGraphicsExtractor g, Runnable r) {
        ScreenRectangle a = scrollArea();
        if (a == null) {
            r.run();
            return;
        }
        g.enableScissor(a.left(), a.top(), a.right(), a.bottom());
        r.run();
        g.disableScissor();
    }

    // ---------------------------------------------------------------- popups

    public void openPopup(MkPopup p) {
        p.owner = this;
        p.place(width, height);
        popups.add(p);
    }

    public void closePopup(MkPopup p) {
        popups.remove(p);
    }

    public void closePopups() {
        popups.clear();
    }

    public boolean hasPopup() {
        return !popups.isEmpty();
    }

    private @Nullable MkPopup top() {
        return popups.isEmpty() ? null : popups.get(popups.size() - 1);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (seeThrough() && minecraft.level != null) {
            g.fill(0, 0, width, height, 0x66000000);
        } else {
            g.fill(0, 0, width, height, MkTheme.BG);
        }
        drawChrome(g, mouseX, mouseY);
    }

    /** Top bar + sidebar background. */
    protected void drawChrome(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, width, TOP, MkTheme.PANEL);
        MkTheme.hline(g, 0, width, TOP - 1, MkTheme.LINE);
        // brand: the green pickaxe logo + "Makeium"
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, PAD - 2, 6, 0, 0, 16, 16, 16, 16);
        int bx = PAD + 18;
        if (width >= 360) {
            MkTheme.boldText(g, Component.literal("Makeium"), bx, 10, MkTheme.TEXT);
            bx += MkTheme.bold().width("Makeium") + 10;
            MkTheme.vline(g, bx, 8, 20, MkTheme.LINE);
            bx += 10;
        }
        int room = width - topActionsWidth - bx - 6;
        if (room > 30) MkTheme.text(g, MkTheme.clip(MkTheme.font(), title, room), bx, 10, MkTheme.MUTED);
        int sw = sidebarWidth();
        if (sw > 0) {
            g.fill(0, TOP, sw, height, MkTheme.PANEL);
            MkTheme.vline(g, sw - 1, TOP, height, MkTheme.LINE);
        }
    }

    /** Content drawn under the widgets (cards, labels). */
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

    /** Content drawn over the widgets but under popups (canvas overlays, drag ghosts). */
    protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        boolean blocked = !popups.isEmpty();
        int mx = blocked ? -10000 : mouseX, my = blocked ? -10000 : mouseY;
        drawBody(g, mx, my, a);
        // fixed widgets first, then the scrolled ones clipped to their area
        List<AbstractWidget> hidden = new ArrayList<>();
        for (AbstractWidget w : scrolled) {
            if (w.visible) {
                w.visible = false;
                hidden.add(w);
            }
        }
        super.extractRenderState(g, mx, my, a);
        for (AbstractWidget w : hidden) w.visible = true;
        if (!hidden.isEmpty()) {
            boolean inside = inScrollArea(mx, my);
            clipToScrollArea(g, () -> {
                for (AbstractWidget w : hidden) w.extractRenderState(g, inside ? mx : -10000, inside ? my : -10000, a);
            });
            int max = maxScroll();
            ScreenRectangle area = scrollArea();
            if (max > 0 && area != null) {
                int h = area.height();
                int thumb = Math.max(16, h * h / (h + max));
                int ty = area.top() + (int) ((h - thumb) * (scroll / (double) max));
                g.fill(area.right() - 3, area.top(), area.right(), area.bottom(), MkTheme.withAlpha(MkTheme.PANEL, 0.8f));
                g.fill(area.right() - 3, ty, area.right(), ty + thumb, MkTheme.LINE_STRONG);
            }
        }
        drawOverlay(g, mx, my, a);
        for (MkPopup p : new ArrayList<>(popups)) {
            g.nextStratum();
            if (p.dims()) g.fill(0, 0, width, height, MkTheme.OVERLAY);
            p.render(g, p == top() ? mouseX : -10000, p == top() ? mouseY : -10000, a);
        }
        g.nextStratum();
        MkToast.render(g, width, height);
    }

    @Override
    public void tick() {
        super.tick();
        for (MkPopup p : new ArrayList<>(popups)) p.tick();
    }

    // ---------------------------------------------------------------- input: popups first

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        MkPopup p = top();
        if (p != null) {
            if (p.contains(e.x(), e.y())) return p.mouseClicked(e, doubleClick) || true;
            if (p.closeOnOutsideClick()) {
                popups.remove(p);
                p.onDismiss();
            }
            return true;
        }
        if (!scrolled.isEmpty() && !inScrollArea(e.x(), e.y())) {
            // scrolled widgets clipped away must not take the click
            List<AbstractWidget> hidden = new ArrayList<>();
            for (AbstractWidget w : scrolled) {
                if (w.visible) {
                    w.visible = false;
                    hidden.add(w);
                }
            }
            try {
                return super.mouseClicked(e, doubleClick);
            } finally {
                for (AbstractWidget w : hidden) w.visible = true;
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        MkPopup p = top();
        if (p != null) return p.mouseReleased(e) || true;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        MkPopup p = top();
        if (p != null) return p.mouseDragged(e, dx, dy) || true;
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        MkPopup p = top();
        if (p != null) return p.mouseScrolled(mx, my, sx, sy) || true;
        if (inScrollArea(mx, my)) {
            // a scrolled child that scrolls itself (a list) wins
            for (AbstractWidget w : scrolled) {
                if (w.isMouseOver(mx, my) && w.mouseScrolled(mx, my, sx, sy)) return true;
            }
            int max = maxScroll();
            int next = Math.max(0, Math.min(max, scroll - (int) Math.round(sy * 20)));
            if (next != scroll) {
                scroll = next;
                onScrolled();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        MkPopup p = top();
        if (p != null) return p.keyPressed(e) || true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        MkPopup p = top();
        if (p != null) return p.charTyped(e) || true;
        return super.charTyped(e);
    }

    @Override
    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        MkPopup p = top();
        if (p instanceof MkWidgetPopup wp) return wp.preeditUpdated(event);
        return super.preeditUpdated(event);
    }

    protected <T extends GuiEventListener & Renderable & NarratableEntry> T add(T w) {
        return addRenderableWidget(w);
    }
}
