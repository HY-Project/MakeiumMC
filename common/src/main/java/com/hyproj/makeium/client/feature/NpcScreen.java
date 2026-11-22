package com.hyproj.makeium.client.feature;

import java.util.ArrayList;
import java.util.List;

import com.hyproj.makeium.client.Replies;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkDialog;
import com.hyproj.makeium.client.ui.MkDropdown;
import com.hyproj.makeium.client.ui.MkField;
import com.hyproj.makeium.client.ui.MkForm;
import com.hyproj.makeium.client.ui.MkList;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.feature.npc.NpcPack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * NPCs and dialog trees (26.3 dialogs): an NPC has a name, an entity type and dialog nodes; each node has a title,
 * text and up to four choices that open another node, run a function or a command, or close. Save & apply writes the
 * "makeium_npc" pack; "spawn here" places the NPC with its click box at your position.
 */
public class NpcScreen extends MkScreen {
    private static final String[] ACTIONS = {"node", "function", "command", "close"};
    private static final List<String> TYPES = List.of("minecraft:villager", "minecraft:mannequin", "minecraft:armor_stand", "minecraft:wandering_trader",
            "minecraft:zombie", "minecraft:skeleton", "minecraft:piglin", "minecraft:allay", "minecraft:cat", "minecraft:fox");
    private static CompoundTag state = new CompoundTag();
    private static int npcIndex = -1, nodeIndex = 0;
    private boolean requested;
    private MkForm form;

    public NpcScreen(Screen parent) {
        super(Component.translatable("makeium.feature.npc"), parent);
    }

    private int listW() {
        return Math.min(160, width / 3);
    }

    @Override
    protected ScreenRectangle scrollArea() {
        int x = PAD + listW() + PAD;
        return new ScreenRectangle(x, TOP, width - x, height - TOP);
    }

    private ListTag npcs() {
        if (!state.contains("npcs")) state.put("npcs", new ListTag());
        return state.getListOrEmpty("npcs");
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70 + 110;
        if (!requested) {
            requested = true;
            PackState.load(NpcPack.PACK, s -> {
                state = s;
                rebuildWidgets();
            });
        }
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        add(new MkButton(width - PAD - 60 - 6 - 104, 4, 104, 20, Component.translatable("makeium.ui.save_apply"), MkButton.Kind.PRIMARY, this::save));
        int lw = listW();
        add(new MkButton(PAD, TOP + 8, lw, 20, Component.translatable("makeium.npc.new"), MkButton.Kind.PRIMARY, () ->
                MkDialog.prompt(this, Component.translatable("makeium.npc.new"), Component.translatable("makeium.npc.id"), "npc_" + (npcs().size() + 1), id -> {
                    CompoundTag npc = new CompoundTag();
                    npc.putString("name", NpcPack.safe(id));
                    npc.putString("display", id);
                    npc.putString("type", "minecraft:villager");
                    ListTag nodes = new ListTag();
                    nodes.add(node("start", "", "Hello, traveller!"));
                    npc.put("nodes", nodes);
                    ListTag l = npcs().copy();
                    l.add(npc);
                    state.put("npcs", l);
                    npcIndex = l.size() - 1;
                    nodeIndex = 0;
                    rebuildWidgets();
                })));
        MkList list = add(new MkList(PAD, TOP + 32, lw, height - TOP - 32 - PAD));
        List<MkList.Item> rows = new ArrayList<>();
        ListTag l = npcs();
        for (int i = 0; i < l.size(); i++) {
            final int idx = i;
            CompoundTag n = l.getCompoundOrEmpty(i);
            rows.add(new MkList.Row(Component.literal(n.getStringOr("display", "")), Component.literal(n.getStringOr("type", "").replace("minecraft:", "")), null, () -> {
                npcIndex = idx;
                nodeIndex = 0;
                scroll = 0;
                rebuildWidgets();
            }).selected(i == npcIndex));
        }
        if (rows.isEmpty()) rows.add(new MkList.Note(Component.translatable("makeium.npc.none"), MkTheme.FAINT));
        list.setItems(rows);
        if (npcIndex >= 0 && npcIndex < l.size()) buildEditor(l.getCompoundOrEmpty(npcIndex));
        else form = null;
    }

    private static CompoundTag node(String id, String title, String body) {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putString("title", title);
        t.putString("body", body);
        ListTag choices = new ListTag();
        CompoundTag c = new CompoundTag();
        c.putString("label", "Bye");
        c.putString("action", "close");
        c.putString("target", "");
        choices.add(c);
        t.put("choices", choices);
        return t;
    }

    private void buildEditor(CompoundTag npc) {
        int x = PAD + listW() + PAD;
        form = new MkForm(this, w -> addScrolled(w), x, TOP + 8 - scroll, Math.min(340, width - x - PAD - 4));
        form.caption(Component.translatable("makeium.npc.npc"));
        form.field(Component.translatable("makeium.npc.display"), npc.getStringOr("display", ""), s -> npc.putString("display", s));
        form.dropdown(Component.translatable("makeium.npc.type"), TYPES, npc.getStringOr("type", "minecraft:villager"), Component::literal, v -> npc.putString("type", v));
        form.buttons(MkButton.Kind.DEFAULT,
                new Component[]{Component.translatable("makeium.npc.spawn_here"), Component.translatable("makeium.ui.delete")},
                new Runnable[]{() -> {
                    save();
                    PackState.runCommand("execute at @s run function " + NpcPack.NS + ":" + npc.getStringOr("name", "npc") + "/spawn");
                }, () -> MkDialog.confirm(this, Component.translatable("makeium.ui.delete"), Component.literal(npc.getStringOr("display", "")), () -> {
                    PackState.runCommand("kill @e[tag=mk_npc_body_" + npc.getStringOr("name", "") + "]");
                    PackState.runCommand("kill @e[tag=mk_npc_" + npc.getStringOr("name", "") + "]");
                    ListTag l = npcs().copy();
                    l.remove(npcIndex);
                    state.put("npcs", l);
                    npcIndex = -1;
                    rebuildWidgets();
                })});

        ListTag nodes = npc.getListOrEmpty("nodes");
        if (nodes.isEmpty()) {
            nodes = new ListTag();
            nodes.add(node("start", "", ""));
            npc.put("nodes", nodes);
        }
        if (nodeIndex >= nodes.size()) nodeIndex = 0;
        form.caption(Component.translatable("makeium.npc.dialog"));
        List<String> ids = new ArrayList<>();
        for (Tag t : nodes) if (t instanceof CompoundTag c) ids.add(c.getStringOr("id", ""));
        final ListTag nodesF = nodes;
        form.dropdown(Component.translatable("makeium.npc.node"), ids, ids.get(nodeIndex), Component::literal, v -> {
            nodeIndex = Math.max(0, ids.indexOf(v));
            scroll = 0;
            rebuildWidgets();
        });
        form.buttons(MkButton.Kind.DEFAULT, new Component[]{Component.translatable("makeium.npc.add_node")}, new Runnable[]{() ->
                MkDialog.prompt(this, Component.translatable("makeium.npc.add_node"), Component.translatable("makeium.npc.id"), "node" + (nodesF.size() + 1), id -> {
                    nodesF.add(node(NpcPack.safe(id), "", ""));
                    nodeIndex = nodesF.size() - 1;
                    rebuildWidgets();
                })});
        CompoundTag node = nodes.getCompoundOrEmpty(nodeIndex);
        form.field(Component.translatable("makeium.npc.title"), node.getStringOr("title", ""), s -> node.putString("title", s));
        form.field(Component.translatable("makeium.npc.body"), node.getStringOr("body", ""), s -> node.putString("body", s));
        ListTag choices = node.getListOrEmpty("choices");
        for (int ci = 0; ci < choices.size(); ci++) {
            CompoundTag c = choices.getCompoundOrEmpty(ci);
            final int cIndex = ci;
            form.caption(Component.translatable("makeium.npc.choice", ci + 1));
            form.field(Component.translatable("makeium.npc.label"), c.getStringOr("label", ""), s -> c.putString("label", s));
            int half = (form.w - 4) / 2;
            MkDropdown<String> action = form.add(new MkDropdown<>(this, form.x, form.y, half, 18, List.of(ACTIONS), c.getStringOr("action", "close"),
                    v -> Component.translatable("makeium.npc.action." + v), v -> {
                c.putString("action", v);
                rebuildWidgets();
            }));
            String act = c.getStringOr("action", "close");
            if (act.equals("node")) {
                form.add(new MkDropdown<>(this, form.x + half + 4, form.y, half, 18, ids, c.getStringOr("target", ids.get(0)), Component::literal, v -> c.putString("target", v)));
            } else if (!act.equals("close")) {
                MkField target = form.add(new MkField(MkTheme.mono(), form.x + half + 4, form.y, half, 18, Component.empty()));
                target.value(c.getStringOr("target", ""));
                target.onChange(s -> c.putString("target", s));
            }
            form.y += 22;
            form.buttons(MkButton.Kind.GHOST, new Component[]{Component.translatable("makeium.npc.remove_choice")}, new Runnable[]{() -> {
                choices.remove(cIndex);
                rebuildWidgets();
            }});
            action.setValue(act);
        }
        if (choices.size() < 4) {
            form.button(Component.translatable("makeium.npc.add_choice"), MkButton.Kind.DEFAULT, () -> {
                CompoundTag c = new CompoundTag();
                c.putString("label", "…");
                c.putString("action", "close");
                c.putString("target", "");
                choices.add(c);
                if (!node.contains("choices")) node.put("choices", choices);
                rebuildWidgets();
            });
        }
    }

    private void save() {
        List<NpcPack.Npc> list = new ArrayList<>();
        for (Tag t : npcs()) {
            if (!(t instanceof CompoundTag n)) continue;
            List<NpcPack.DialogNode> nodes = new ArrayList<>();
            for (Tag nt : n.getListOrEmpty("nodes")) {
                if (!(nt instanceof CompoundTag node)) continue;
                List<NpcPack.Choice> choices = new ArrayList<>();
                for (Tag ct : node.getListOrEmpty("choices")) {
                    if (ct instanceof CompoundTag c) choices.add(new NpcPack.Choice(c.getStringOr("label", ""), c.getStringOr("action", "close"), c.getStringOr("target", "")));
                }
                nodes.add(new NpcPack.DialogNode(node.getStringOr("id", "start"), node.getStringOr("title", ""), node.getStringOr("body", ""), choices));
            }
            list.add(new NpcPack.Npc(n.getStringOr("name", "npc"), n.getStringOr("display", "NPC"), n.getStringOr("type", "minecraft:villager"), nodes));
        }
        PackState.save(NpcPack.PACK, "Makeium NPCs and dialogs", NpcPack.compile(list), state);
        MkToast.ok(Component.translatable("makeium.npc.saved", list.size()));
    }

    @Override
    public void removed() {
        Replies.off("dp/load");
        super.removed();
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (form != null) {
            clipToScrollArea(g, () -> {
                form.draw(g);
                MkTheme.paragraph(g, Component.translatable("makeium.npc.help"), form.x, form.y + 4, form.w, MkTheme.FAINT);
            });
        } else {
            int x = PAD + listW() + PAD;
            MkTheme.paragraph(g, Component.translatable("makeium.npc.intro"), x, TOP + 12, width - x - PAD, MkTheme.MUTED);
        }
    }
}
