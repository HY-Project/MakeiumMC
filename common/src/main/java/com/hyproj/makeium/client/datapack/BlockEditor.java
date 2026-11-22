package com.hyproj.makeium.client.datapack;

import java.util.ArrayList;
import java.util.List;

import com.hyproj.makeium.client.ui.MkDropdown;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.feature.datapack.BlockDef;
import com.hyproj.makeium.feature.datapack.BlockDefs;
import com.hyproj.makeium.feature.datapack.Node;
import com.hyproj.makeium.feature.datapack.Project;
import com.hyproj.makeium.feature.datapack.Script;
import com.hyproj.makeium.feature.datapack.SlotDef;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The block-coding workspace: a category list and block palette on the left, the scrollable / zoomable canvas on the
 * right. Drag a block out of the palette to create it; drag a placed block to move it together with everything
 * below it; drop it under another block, into a C block's mouth, or anywhere to start a new stack; drop it back
 * on the palette (or press Delete) to remove it. Click an input to edit it; right-click a block for more.
 */
public class BlockEditor extends AbstractWidget {
    public static final int PALETTE_W = 136;
    private static final int CHIP = 14, CHIP_GAP = 3;

    private final MkScreen screen;
    private final Project project;
    private final Runnable onChange;
    private BlockDef.Category category = BlockDef.Category.EVENTS;
    private double paletteScroll;
    private double panX = -20, panY = -20;
    private float zoom = 1f;

    /** Layout of the current frame (canvas units). */
    private final List<BlockRender.Laid> laid = new ArrayList<>();
    private final List<Script> laidScripts = new ArrayList<>();

    // dragging
    private @Nullable List<Node> dragStack;
    private @Nullable Script dragScript;
    private double dragOffX, dragOffY, dragX, dragY;
    private @Nullable Drop drop;
    private boolean panning;
    private double pressX, pressY;
    private BlockRender.@Nullable Laid pressed;
    private boolean pressedFromPalette;
    private @Nullable BlockDef pressedDef;

    private @Nullable Node selected;
    private @Nullable Script selectedScript;
    private List<String> commandRoots = List.of();

    private record Drop(List<Node> list, int index, int x, int y) {}

    public BlockEditor(MkScreen screen, int x, int y, int w, int h, Project project, Runnable onChange) {
        super(x, y, w, h, Component.empty());
        this.screen = screen;
        this.project = project;
        this.onChange = onChange;
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            List<String> roots = new ArrayList<>();
            conn.getCommands().getRoot().getChildren().forEach(c -> roots.add(c.getName()));
            roots.sort(String::compareTo);
            commandRoots = roots;
        }
    }

    public @Nullable Script selectedScript() {
        return selectedScript;
    }

    private int canvasLeft() {
        return getX() + PALETTE_W;
    }

    private double toCanvasX(double mx) {
        return (mx - canvasLeft()) / zoom + panX;
    }

    private double toCanvasY(double my) {
        return (my - getY()) / zoom + panY;
    }

    // ---------------------------------------------------------------- palette content

    private List<BlockDef> paletteDefs() {
        List<BlockDef> defs = new ArrayList<>(BlockDefs.inCategory(category));
        if (category == BlockDef.Category.COMMANDS) {
            for (String root : commandRoots) defs.add(BlockDefs.command(root));
        }
        return defs;
    }

    private int chipsPerRow() {
        return (PALETTE_W - 8) / (CHIP + CHIP_GAP);
    }

    private int chipRows() {
        int n = BlockDef.Category.values().length;
        return (n + chipsPerRow() - 1) / chipsPerRow();
    }

    private int chipX(int i) {
        return getX() + 5 + (i % chipsPerRow()) * (CHIP + CHIP_GAP);
    }

    private int chipY(int i) {
        return getY() + 5 + (i / chipsPerRow()) * (CHIP + CHIP_GAP);
    }

    private int paletteTop() {
        return getY() + 5 + chipRows() * (CHIP + CHIP_GAP) + 16;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        // palette background
        g.fill(x, y, x + PALETTE_W, y + h, MkTheme.PANEL);
        MkTheme.vline(g, x + PALETTE_W - 1, y, y + h, MkTheme.LINE);
        BlockDef.Category[] cats = BlockDef.Category.values();
        BlockDef.Category hoverCat = null;
        for (int i = 0; i < cats.length; i++) {
            BlockDef.Category c = cats[i];
            int chx = chipX(i), chy = chipY(i);
            boolean sel = c == category;
            boolean hot = mouseX >= chx && mouseX < chx + CHIP && mouseY >= chy && mouseY < chy + CHIP;
            if (hot) hoverCat = c;
            g.fill(chx, chy, chx + CHIP, chy + CHIP, sel || hot ? c.color : MkTheme.withAlpha(c.color, 0.55f));
            if (sel) MkTheme.border(g, chx - 1, chy - 1, CHIP + 2, CHIP + 2, 0xFFFFFFFF);
        }
        BlockDef.Category shown = hoverCat != null ? hoverCat : category;
        MkTheme.boldText(g, MkTheme.clip(MkTheme.bold(), Component.translatable("makeium.category." + shown.name().toLowerCase(java.util.Locale.ROOT)), PALETTE_W - 10),
                x + 5, paletteTop() - 13, shown.color);
        MkTheme.hline(g, x, x + PALETTE_W - 1, paletteTop() - 3, MkTheme.LINE);
        g.enableScissor(x, paletteTop(), x + PALETTE_W - 1, y + h);
        int py = paletteTop() + 4 - (int) paletteScroll;
        for (BlockDef d : paletteDefs()) {
            int eh = BlockRender.paletteEntryHeight(d);
            if (py + eh > paletteTop() - 20 && py < y + h) {
                boolean hot = dragStack == null && mouseX >= x + 6 && mouseX < x + PALETTE_W - 6 && mouseY >= py && mouseY < py + eh && mouseY >= paletteTop();
                BlockRender.drawPaletteEntry(g, d, x + 6, py, PALETTE_W - 12, hot);
            }
            py += eh + 5;
        }
        g.disableScissor();

        // canvas
        int cx0 = canvasLeft();
        g.fill(cx0, y, x + w, y + h, 0xFF19191A);
        g.enableScissor(cx0, y, x + w, y + h);
        drawGrid(g, cx0, y, x + w, y + h);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) (cx0 - panX * zoom), (float) (y - panY * zoom));
        pose.scale(zoom, zoom);
        relayout();
        double mcx = toCanvasX(mouseX), mcy = toCanvasY(mouseY);
        boolean inCanvas = mouseX >= cx0 && mouseX < x + w && mouseY >= y && mouseY < y + h;
        BlockRender.Laid hover = inCanvas && dragStack == null ? hit(mcx, mcy) : null;
        for (BlockRender.Laid l : laid) {
            BlockRender.draw(g, l, l == hover, l.node == selected);
        }
        // loose stacks (no event) are dimmed with a hint
        for (Script s : laidScripts) {
            Node head = s.head();
            if (head != null && BlockDefs.get(head.def).shape() != BlockDef.Shape.HAT) {
                g.text(BlockRender.font(), Component.translatable("makeium.dp.loose"), (int) s.x, (int) s.y - 10, MkTheme.FAINT, false);
            }
        }
        if (drop != null) {
            g.fill(drop.x, drop.y - 1, drop.x + 60, drop.y + 2, 0xFFFFFFFF);
        }
        if (dragStack != null) {
            List<BlockRender.Laid> ghost = new ArrayList<>();
            BlockRender.layoutList(dragStack, (int) (dragX - dragOffX), (int) (dragY - dragOffY), 0, ghost);
            for (BlockRender.Laid l : ghost) BlockRender.draw(g, l, false, false);
        }
        pose.popMatrix();
        g.disableScissor();
        // zoom badge
        String z = Math.round(zoom * 100) + "%";
        MkTheme.text(g, Component.literal(z), x + w - MkTheme.font().width(z) - 8, y + h - 14, MkTheme.FAINT);
        if (project.scripts.isEmpty()) {
            MkTheme.textCentered(g, MkTheme.font(), Component.translatable("makeium.dp.empty_canvas"), cx0 + (x + w - cx0) / 2, y + h / 2 - 4, MkTheme.FAINT);
        }
        if (dragStack != null && mouseX < cx0) {
            g.fill(x, y, x + PALETTE_W, y + h, 0x40D9443A);
            MkTheme.textCentered(g, MkTheme.font(), Component.translatable("makeium.dp.drop_delete"), x + PALETTE_W / 2, y + h / 2, 0xFFFFFFFF);
        }
    }

    private void drawGrid(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        int step = Math.max(8, Math.round(24 * zoom));
        int ox = (int) Math.floorMod((long) Math.round(-panX * zoom), step);
        int oy = (int) Math.floorMod((long) Math.round(-panY * zoom), step);
        for (int gx = x0 + ox; gx < x1; gx += step) {
            for (int gy = y0 + oy; gy < y1; gy += step) g.fill(gx, gy, gx + 1, gy + 1, 0xFF2C2C2F);
        }
    }

    private void relayout() {
        laid.clear();
        laidScripts.clear();
        for (Script s : project.scripts) {
            laidScripts.add(s);
            BlockRender.layoutList(s.nodes, (int) s.x, (int) s.y, 0, laid);
        }
    }

    private BlockRender.@Nullable Laid hit(double cx, double cy) {
        for (int i = laid.size() - 1; i >= 0; i--) {
            BlockRender.Laid l = laid.get(i);
            if (cx >= l.x && cx < l.x + l.w && cy >= l.y && cy < l.y + l.headerH) return l;
        }
        // C-block arms / foot also grab the block
        for (int i = laid.size() - 1; i >= 0; i--) {
            BlockRender.Laid l = laid.get(i);
            if (cx >= l.x && cx < l.x + BlockRender.INDENT && cy >= l.y && cy < l.y + l.totalH) return l;
        }
        return null;
    }

    private BlockRender.@Nullable SlotRect hitSlot(double cx, double cy) {
        for (int i = laid.size() - 1; i >= 0; i--) {
            for (BlockRender.SlotRect r : laid.get(i).slots) {
                if (cx >= r.x() && cx < r.x() + r.w() && cy >= r.y() && cy < r.y() + r.h()) return r;
            }
        }
        return null;
    }

    private @Nullable Script scriptOf(List<Node> list) {
        for (Script s : project.scripts) if (s.nodes == list) return s;
        return null;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (!isMouseOver(e.x(), e.y())) return false;
        setFocused(true);
        double mx = e.x(), my = e.y();
        if (mx < canvasLeft()) {
            // category chips
            BlockDef.Category[] cats = BlockDef.Category.values();
            for (int i = 0; i < cats.length; i++) {
                if (mx >= chipX(i) && mx < chipX(i) + CHIP && my >= chipY(i) && my < chipY(i) + CHIP) {
                    category = cats[i];
                    paletteScroll = 0;
                    return true;
                }
            }
            // palette block
            int py = paletteTop() + 4 - (int) paletteScroll;
            for (BlockDef d : paletteDefs()) {
                int eh = BlockRender.paletteEntryHeight(d);
                if (my >= py && my < py + eh && my >= paletteTop()) {
                    pressedFromPalette = true;
                    pressedDef = d;
                    pressX = mx;
                    pressY = my;
                    dragOffX = (mx - getX() - 6) / zoom;
                    dragOffY = (my - py) / zoom;
                    return true;
                }
                py += eh + 5;
            }
            return true;
        }
        relayout();
        double cx = toCanvasX(mx), cy = toCanvasY(my);
        if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            BlockRender.Laid l = hit(cx, cy);
            if (l != null) openContextMenu(l, (int) mx, (int) my);
            return true;
        }
        BlockRender.SlotRect slot = hitSlot(cx, cy);
        if (slot != null) {
            editSlot(slot, (int) mx, (int) my);
            return true;
        }
        BlockRender.Laid l = hit(cx, cy);
        pressX = mx;
        pressY = my;
        if (l != null) {
            pressed = l;
            selected = l.node;
            Script s = scriptOf(l.list);
            selectedScript = s != null ? s : findScriptContaining(l.node);
            return true;
        }
        selected = null;
        panning = true;
        return true;
    }

    private @Nullable Script findScriptContaining(Node n) {
        for (Script s : project.scripts) if (contains(s.nodes, n)) return s;
        return null;
    }

    private static boolean contains(List<Node> list, Node n) {
        for (Node m : list) {
            if (m == n || contains(m.body, n) || contains(m.elseBody, n)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        double mx = e.x(), my = e.y();
        if (panning) {
            panX -= dx / zoom;
            panY -= dy / zoom;
            return true;
        }
        if (dragStack == null && Math.hypot(mx - pressX, my - pressY) > 3) {
            if (pressedFromPalette && pressedDef != null) {
                Node n = Node.create(pressedDef);
                if (pressedDef.id().equals("ev_function")) n.values.put("name", "function_" + (project.scripts.size() + 1));
                dragStack = new ArrayList<>(List.of(n));
                selected = n;
            } else if (pressed != null) {
                startDrag(pressed, mx, my);
            }
        }
        if (dragStack != null) {
            dragX = toCanvasX(mx);
            dragY = toCanvasY(my);
            drop = mx >= canvasLeft() ? findDrop() : null;
            return true;
        }
        return false;
    }

    private void startDrag(BlockRender.Laid l, double mx, double my) {
        List<Node> list = l.list;
        Script s = scriptOf(list);
        if (s != null && l.index == 0) {
            // grabbing the first block moves the whole script
            dragScript = s;
            project.scripts.remove(s);
            dragStack = new ArrayList<>(s.nodes);
        } else {
            dragStack = new ArrayList<>(list.subList(l.index, list.size()));
            list.subList(l.index, list.size()).clear();
            if (s != null && s.nodes.isEmpty()) project.scripts.remove(s);
        }
        dragOffX = toCanvasX(mx) - l.x;
        dragOffY = toCanvasY(my) - l.y;
        dragX = toCanvasX(mx);
        dragY = toCanvasY(my);
        onChange.run();
    }

    private @Nullable Drop findDrop() {
        if (dragStack == null || dragStack.isEmpty()) return null;
        boolean isHat = BlockDefs.get(dragStack.get(0).def).shape() == BlockDef.Shape.HAT;
        if (isHat) return null;
        relayout();
        double tx = dragX - dragOffX, ty = dragY - dragOffY;
        Drop best = null;
        double bestD = 22;
        for (BlockRender.Laid l : laid) {
            // after this block
            double d = Math.hypot(tx - l.x, ty - (l.y + l.totalH));
            if (d < bestD) {
                bestD = d;
                best = new Drop(l.list, l.index + 1, l.x, l.y + l.totalH);
            }
            BlockDef def = BlockDefs.get(l.node.def);
            if (def.shape() == BlockDef.Shape.C || def.shape() == BlockDef.Shape.C2) {
                d = Math.hypot(tx - l.bodyX, ty - l.bodyY);
                if (d < bestD) {
                    bestD = d;
                    best = new Drop(l.node.body, 0, l.bodyX, l.bodyY);
                }
                if (def.shape() == BlockDef.Shape.C2) {
                    d = Math.hypot(tx - l.bodyX, ty - l.elseY);
                    if (d < bestD) {
                        bestD = d;
                        best = new Drop(l.node.elseBody, 0, l.bodyX, l.elseY);
                    }
                }
            }
        }
        // on top of a loose stack's first block
        for (Script s : project.scripts) {
            Node head = s.head();
            if (head == null || BlockDefs.get(head.def).shape() == BlockDef.Shape.HAT) continue;
            double d = Math.hypot(tx - s.x, ty + 18 - s.y);
            if (d < bestD) {
                bestD = d;
                best = new Drop(s.nodes, 0, (int) s.x, (int) s.y);
            }
        }
        return best;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        double mx = e.x(), my = e.y();
        if (dragStack != null) {
            if (mx < canvasLeft()) {
                // dropped on the palette: deleted
                selected = null;
            } else if (drop != null) {
                Drop d = drop;
                Script target = scriptOf(d.list);
                d.list.addAll(d.index, dragStack);
                if (target != null && d.index == 0) {
                    // dropped on top of a loose stack: the stack grows upwards
                    int added = BlockRender.layoutList(dragStack, 0, 0, 0, new ArrayList<>());
                    target.y = d.y - added;
                }
            } else {
                Script s = dragScript != null ? dragScript : new Script(0, 0);
                s.x = Math.round(dragX - dragOffX);
                s.y = Math.round(dragY - dragOffY);
                s.nodes.clear();
                s.nodes.addAll(dragStack);
                project.scripts.add(s);
                selectedScript = s;
            }
            dragStack = null;
            dragScript = null;
            drop = null;
            onChange.run();
        }
        panning = false;
        pressed = null;
        pressedFromPalette = false;
        pressedDef = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!isMouseOver(mx, my)) return false;
        if (mx < canvasLeft()) {
            int total = 0;
            for (BlockDef d : paletteDefs()) total += BlockRender.paletteEntryHeight(d) + 5;
            double max = Math.max(0, total - (getY() + getHeight() - paletteTop()) + 10);
            paletteScroll = Math.max(0, Math.min(max, paletteScroll - sy * 20));
            return true;
        }
        if (InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)) {
            double cx = toCanvasX(mx), cy = toCanvasY(my);
            zoom = Math.max(0.4f, Math.min(2f, zoom * (sy > 0 ? 1.1f : 1 / 1.1f)));
            panX = cx - (mx - canvasLeft()) / zoom;
            panY = cy - (my - getY()) / zoom;
        } else if (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)) {
            panX -= sy * 30 / zoom;
        } else {
            panY -= sy * 30 / zoom;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (selected != null && (e.key() == InputConstants.KEY_DELETE || e.key() == InputConstants.KEY_BACKSPACE)) {
            delete(selected);
            return true;
        }
        if (selected != null && e.hasControlDown() && e.key() == InputConstants.KEY_D) {
            duplicate(selected);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- actions

    private void delete(Node n) {
        for (Script s : new ArrayList<>(project.scripts)) {
            if (s.nodes.isEmpty()) continue;
            if (s.nodes.get(0) == n) {
                project.scripts.remove(s);
                break;
            }
            if (removeFrom(s.nodes, n)) break;
        }
        selected = null;
        onChange.run();
    }

    private static boolean removeFrom(List<Node> list, Node n) {
        for (int i = 0; i < list.size(); i++) {
            Node m = list.get(i);
            if (m == n) {
                list.remove(i);
                return true;
            }
            if (removeFrom(m.body, n) || removeFrom(m.elseBody, n)) return true;
        }
        return false;
    }

    private void duplicate(Node n) {
        relayout();
        for (BlockRender.Laid l : laid) {
            if (l.node == n) {
                Script s = new Script(l.x + 30, l.y + 30);
                Script owner = scriptOf(l.list);
                if (owner != null && l.index == 0) {
                    for (Node m : owner.nodes) s.nodes.add(m.deepCopy());
                } else {
                    s.nodes.add(n.deepCopy());
                }
                project.scripts.add(s);
                onChange.run();
                return;
            }
        }
    }

    private void editSlot(BlockRender.SlotRect r, int mx, int my) {
        BlockDef d = BlockDefs.get(r.node().def);
        SlotDef s = d.slot(r.slot());
        if (s == null) return;
        String prefix = d.id().startsWith("cmd/") ? d.id().substring(4) + " " : "";
        SlotPopup.open(screen, mx - 10, my + 10, s, r.node().value(s.name(), ""), prefix, v -> {
            r.node().values.put(s.name(), v);
            onChange.run();
        });
    }

    private void openContextMenu(BlockRender.Laid l, int mx, int my) {
        Node n = l.node;
        List<String> actions = new ArrayList<>(List.of("duplicate", "comment", n.disabled ? "enable" : "disable", "delete"));
        screen.openPopup(new MkDropdown.ListPopup<>(mx, my, 150, actions, null, a -> Component.translatable("makeium.dp.menu." + a), a -> {
            switch (a) {
                case "duplicate" -> duplicate(n);
                case "comment" -> com.hyproj.makeium.client.ui.MkDialog.prompt(screen, Component.translatable("makeium.dp.menu.comment"), Component.empty(), n.comment, v -> {
                    n.comment = v.strip();
                    onChange.run();
                });
                case "enable", "disable" -> {
                    n.disabled = !n.disabled;
                    onChange.run();
                }
                default -> delete(n);
            }
        }));
    }

    public void resetView() {
        panX = -20;
        panY = -20;
        zoom = 1f;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
