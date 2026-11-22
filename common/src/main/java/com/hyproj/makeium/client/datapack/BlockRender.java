package com.hyproj.makeium.client.datapack;

import java.util.ArrayList;
import java.util.List;

import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.feature.datapack.BlockDef;
import com.hyproj.makeium.feature.datapack.BlockDefs;
import com.hyproj.makeium.feature.datapack.Node;
import com.hyproj.makeium.feature.datapack.SlotDef;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;

/**
 * Layout and drawing of Scratch-style blocks in canvas units. A block's label comes from the language file
 * ({@code makeium.block.<id>}) where {@code {slot}} marks an input; inputs are dark inset boxes showing the value.
 * C blocks draw an arm on the left of their body and a foot below it; hats get a cap on top.
 */
public final class BlockRender {
    public static final int H = 18, SLOT_H = 13, PADX = 6, INDENT = 14, FOOT = 9, ELSE_H = 14, HAT_CAP = 6, MIN_W = 70, EMPTY_BODY = 12;

    public record Token(String text, String slot) {}

    public record SlotRect(Node node, String slot, int x, int y, int w, int h) {}

    /** A node placed by a layout pass: where it is and what it owns. */
    public static final class Laid {
        public Node node;
        public List<Node> list;
        public int index;
        public int x, y, w, headerH, totalH;
        public final List<SlotRect> slots = new ArrayList<>();
        /** C blocks: the body's top-left (and else body's). */
        public int bodyX, bodyY, bodyH, elseY, elseH;
        public int depth;
    }

    private BlockRender() {}

    public static Font font() {
        return MkTheme.font();
    }

    /** Label tokens for a block: literal text and {slot} inputs. */
    public static List<Token> tokens(BlockDef d) {
        String key = "makeium.block." + d.id();
        String label;
        if (d.id().startsWith("cmd/")) {
            label = "/" + d.id().substring(4) + " {args}";
        } else if (Language.getInstance().has(key)) {
            label = Language.getInstance().getOrDefault(key);
        } else {
            label = d.id() + (d.slots().isEmpty() ? "" : " ") + String.join(" ", d.slots().stream().map(s -> "{" + s.name() + "}").toList());
        }
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < label.length()) {
            int open = label.indexOf('{', i);
            if (open < 0) {
                out.add(new Token(label.substring(i), null));
                break;
            }
            int close = label.indexOf('}', open);
            if (close < 0) {
                out.add(new Token(label.substring(i), null));
                break;
            }
            if (open > i) out.add(new Token(label.substring(i, open), null));
            String slot = label.substring(open + 1, close);
            if (d.slot(slot) != null) out.add(new Token(null, slot));
            else out.add(new Token(label.substring(open, close + 1), null));
            i = close + 1;
        }
        // slots the label forgot still get a box at the end
        for (SlotDef s : d.slots()) {
            if (out.stream().noneMatch(t -> s.name().equals(t.slot))) out.add(new Token(null, s.name()));
        }
        return out;
    }

    public static String slotText(Node n, SlotDef s) {
        String v = n.value(s.name(), "");
        if (v.isEmpty()) return s.type() == SlotDef.Type.NUMBER ? "" : "…";
        if (v.length() > 42) v = v.substring(0, 40) + "…";
        return v;
    }

    public static int slotWidth(Node n, SlotDef s) {
        return Math.max(16, font().width(slotText(n, s)) + 8 + (s.type() == SlotDef.Type.CHOICE || s.type() == SlotDef.Type.REGISTRY ? 7 : 0));
    }

    public static int headerWidth(Node n) {
        BlockDef d = BlockDefs.get(n.def);
        int w = PADX;
        for (Token t : tokens(d)) {
            if (t.slot != null) w += slotWidth(n, d.slot(t.slot)) + 3;
            else w += font().width(t.text);
        }
        w += PADX;
        if (!n.comment.isEmpty()) w = Math.max(w, font().width("// " + n.comment) + 2 * PADX);
        return Math.max(MIN_W, w);
    }

    public static int headerHeight(Node n) {
        BlockDef d = BlockDefs.get(n.def);
        int h = H + (n.comment.isEmpty() ? 0 : 11);
        if (d.shape() == BlockDef.Shape.HAT) h += HAT_CAP;
        return h;
    }

    /** Lays out a vertical list starting at (x, y); returns its height. */
    public static int layoutList(List<Node> list, int x, int y, int depth, List<Laid> out) {
        int cy = y;
        for (int i = 0; i < list.size(); i++) {
            Laid l = layoutNode(list, i, x, cy, depth, out);
            cy += l.totalH;
        }
        return cy - y;
    }

    public static Laid layoutNode(List<Node> list, int index, int x, int y, int depth, List<Laid> out) {
        Node n = list.get(index);
        BlockDef d = BlockDefs.get(n.def);
        Laid l = new Laid();
        l.node = n;
        l.list = list;
        l.index = index;
        l.x = x;
        l.y = y;
        l.depth = depth;
        l.headerH = headerHeight(n);
        l.w = headerWidth(n);
        out.add(l);
        // slot rects
        int sx = x + PADX;
        int ty = y + (d.shape() == BlockDef.Shape.HAT ? HAT_CAP : 0) + (n.comment.isEmpty() ? 0 : 11);
        for (Token t : tokens(d)) {
            if (t.slot != null) {
                int sw = slotWidth(n, d.slot(t.slot));
                l.slots.add(new SlotRect(n, t.slot, sx, ty + (H - SLOT_H) / 2, sw, SLOT_H));
                sx += sw + 3;
            } else {
                sx += font().width(t.text);
            }
        }
        int total = l.headerH;
        if (d.shape() == BlockDef.Shape.C || d.shape() == BlockDef.Shape.C2) {
            l.bodyX = x + INDENT;
            l.bodyY = y + l.headerH;
            int bh = layoutList(n.body, l.bodyX, l.bodyY, depth + 1, out);
            l.bodyH = Math.max(EMPTY_BODY, bh);
            total += l.bodyH;
            int maxW = l.w;
            for (Laid c : out) if (c.depth > depth && c.y >= l.bodyY && c.y < l.bodyY + l.bodyH) maxW = Math.max(maxW, c.x - x + c.w);
            if (d.shape() == BlockDef.Shape.C2) {
                l.elseY = l.bodyY + l.bodyH + ELSE_H;
                int eh = layoutList(n.elseBody, l.bodyX, l.elseY, depth + 1, out);
                l.elseH = Math.max(EMPTY_BODY, eh);
                total += ELSE_H + l.elseH;
                for (Laid c : out) if (c.depth > depth && c.y >= l.elseY && c.y < l.elseY + l.elseH) maxW = Math.max(maxW, c.x - x + c.w);
            }
            total += FOOT;
            l.w = Math.max(l.w, Math.min(maxW, l.w + 400));
        }
        l.totalH = total;
        return l;
    }

    // ---------------------------------------------------------------- drawing

    public static int color(Node n) {
        int c = BlockDefs.get(n.def).category().color;
        return n.disabled ? MkTheme.mix(c, 0xFF606060, 0.7f) : c;
    }

    private static int darker(int c) {
        return MkTheme.mix(c, 0xFF000000, 0.3f);
    }

    public static void draw(GuiGraphicsExtractor g, Laid l, boolean hover, boolean selected) {
        Node n = l.node;
        BlockDef d = BlockDefs.get(n.def);
        int c = color(n);
        int dark = darker(c);
        int x = l.x, y = l.y, w = l.w;
        boolean cShape = d.shape() == BlockDef.Shape.C || d.shape() == BlockDef.Shape.C2;
        // shadow
        g.fill(x + 1, y + 2, x + w + 1, y + l.headerH + 2, 0x40000000);
        if (d.shape() == BlockDef.Shape.HAT) {
            g.fill(x, y, x + Math.min(w, 60), y + HAT_CAP, c);
            g.fill(x, y + HAT_CAP - 1, x + Math.min(w, 60), y + HAT_CAP, MkTheme.mix(c, 0xFFFFFFFF, 0.25f));
        }
        int hy = y + (d.shape() == BlockDef.Shape.HAT ? HAT_CAP : 0);
        g.fill(x, hy, x + w, y + l.headerH, c);
        g.fill(x, y + l.headerH - 1, x + w, y + l.headerH, dark);
        // connector notch on top (statements) to hint the stacking
        if (d.shape() != BlockDef.Shape.HAT) g.fill(x + 8, hy, x + 20, hy + 2, dark);
        if (cShape) {
            int armBottom = l.bodyY + l.bodyH;
            g.fill(x, l.bodyY, x + INDENT - 2, armBottom, c);
            g.fill(x + INDENT - 3, l.bodyY, x + INDENT - 2, armBottom, dark);
            int footY = armBottom;
            if (d.shape() == BlockDef.Shape.C2) {
                g.fill(x, armBottom, x + w, armBottom + ELSE_H, c);
                g.text(font(), net.minecraft.network.chat.Component.translatable("makeium.block.else"), x + PADX, armBottom + 3, 0xFFFFFFFF, false);
                int elseBottom = l.elseY + l.elseH;
                g.fill(x, l.elseY, x + INDENT - 2, elseBottom, c);
                g.fill(x + INDENT - 3, l.elseY, x + INDENT - 2, elseBottom, dark);
                footY = elseBottom;
            }
            g.fill(x, footY, x + Math.max(40, Math.min(w, 80)), footY + FOOT, c);
            g.fill(x, footY + FOOT - 1, x + Math.max(40, Math.min(w, 80)), footY + FOOT, dark);
            if (n.body.isEmpty()) g.fill(l.bodyX + 2, l.bodyY + 3, l.bodyX + 40, l.bodyY + EMPTY_BODY - 3, 0x30000000);
        }
        if (selected) MkTheme.border(g, x - 1, y - 1, w + 2, l.headerH + 2, 0xFFFFFFFF);
        else if (hover) MkTheme.border(g, x - 1, y - 1, w + 2, l.headerH + 2, 0x80FFFFFF);
        // comment line
        int ty = hy;
        if (!n.comment.isEmpty()) {
            g.text(font(), "// " + n.comment, x + PADX, ty + 3, 0xD0FFFFFF, false);
            ty += 11;
        }
        // label + slots
        int tx = x + PADX;
        for (Token t : tokens(d)) {
            if (t.slot != null) {
                SlotDef s = d.slot(t.slot);
                int sw = slotWidth(n, s);
                int sy = ty + (H - SLOT_H) / 2;
                g.fill(tx, sy, tx + sw, sy + SLOT_H, 0x55000000);
                g.text(font(), slotText(n, s), tx + 4, sy + 3, 0xFFFFFFFF, false);
                if (s.type() == SlotDef.Type.CHOICE || s.type() == SlotDef.Type.REGISTRY) {
                    int ax = tx + sw - 8, ay = sy + 5;
                    g.fill(ax, ay, ax + 5, ay + 1, 0xC0FFFFFF);
                    g.fill(ax + 1, ay + 1, ax + 4, ay + 2, 0xC0FFFFFF);
                    g.fill(ax + 2, ay + 2, ax + 3, ay + 3, 0xC0FFFFFF);
                }
                tx += sw + 3;
            } else {
                g.text(font(), t.text, tx, ty + 5, 0xFFFFFFFF, false);
                tx += font().width(t.text);
            }
        }
    }

    /** A compact palette entry (header only), drawn at (x, y); returns its height. */
    public static int drawPaletteEntry(GuiGraphicsExtractor g, BlockDef d, int x, int y, int maxW, boolean hover) {
        Node n = Node.create(d);
        List<Laid> out = new ArrayList<>();
        List<Node> single = new ArrayList<>(List.of(n));
        Laid l = layoutNode(single, 0, x, y, 0, out);
        g.enableScissor(x - 1, y - 1, x + maxW, y + l.totalH + 2);
        draw(g, l, hover, false);
        g.disableScissor();
        return l.totalH;
    }

    public static int paletteEntryHeight(BlockDef d) {
        Node n = Node.create(d);
        int h = headerHeight(n);
        if (d.shape() == BlockDef.Shape.C || d.shape() == BlockDef.Shape.C2) h += EMPTY_BODY + FOOT + (d.shape() == BlockDef.Shape.C2 ? ELSE_H + EMPTY_BODY : 0);
        return h;
    }
}
