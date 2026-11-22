package com.hyproj.makeium.client.datapack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.hyproj.makeium.client.ui.MkWidgetPopup;
import com.hyproj.makeium.feature.datapack.BlockCompiler;
import com.hyproj.makeium.feature.datapack.BlockDefs;
import com.hyproj.makeium.feature.datapack.JsonTemplates;
import com.hyproj.makeium.feature.datapack.Node;
import com.hyproj.makeium.feature.datapack.Project;
import com.hyproj.makeium.feature.datapack.Script;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.server.ServerFeatures;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * The Datapack Factory. Pages: block coding (scripts), files (JSON registry files and hand-written functions, for
 * every 26.3 datapack type), pack settings. Save compiles the scripts, writes the pack into the world's datapacks
 * folder through the server, reloads, and lists what the server rejected.
 */
public class DatapackScreen extends MkScreen {
    private enum Page { SCRIPTS, FILES, SETTINGS }

    /** Kept across openings so closing the screen never loses work. */
    private static Project current;
    private static boolean dirty;
    private static List<String> packs = new ArrayList<>();
    private static List<String> registries = new ArrayList<>();
    private static List<String> problems = new ArrayList<>();
    private static Boolean showCode;
    private static Page page = Page.SCRIPTS;
    private static String selectedFile;

    private BlockEditor editor;
    private MultiLineEditBox fileBox;
    private MkForm form;
    private double codeScroll;

    public DatapackScreen(Screen parent) {
        super(Component.translatable("makeium.feature.datapack"), parent);
        if (current == null) {
            current = new Project();
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.getSingleplayerServer() != null) {
                String world = BlockCompiler.sanitize(mc.getSingleplayerServer().getWorldData().getLevelName());
                current.pack = world;
                current.namespace = world;
            }
        }
    }

    @Override
    protected int sidebarWidth() {
        return width < 600 ? 84 : 112;
    }

    @Override
    protected void init() {
        super.init();
        Replies.on("dp/list", d -> {
            packs = strings(d.getListOrEmpty("packs"));
            registries = strings(d.getListOrEmpty("registries"));
        });
        Replies.on("dp/load", d -> {
            if (d.contains("error")) {
                MkToast.error(Component.literal(d.getStringOr("error", "")));
                return;
            }
            current = Project.fromPackFiles(d.getStringOr("name", "pack"), ServerFeatures.fromTag(d.getCompoundOrEmpty("files")));
            dirty = false;
            problems = new ArrayList<>();
            selectedFile = null;
            MkToast.ok(Component.translatable("makeium.dp.loaded", current.pack));
            rebuildWidgets();
        });
        Replies.on("dp/saved", d -> {
            if (d.contains("error")) {
                MkToast.error(Component.translatable("makeium.dp.save_failed", d.getStringOr("error", "")));
                return;
            }
            problems = strings(d.getListOrEmpty("problems"));
            if (d.getBooleanOr("rejoin", false)) MkToast.info(Component.translatable("makeium.dp.needs_rejoin"));
            if (problems.isEmpty()) MkToast.ok(Component.translatable("makeium.dp.saved", d.getStringOr("name", "")));
            else MkToast.error(Component.translatable("makeium.dp.saved_problems", problems.size()));
            Net.sendToServer("dp/list");
        });
        Replies.on("dp/validate", d -> {
            problems = strings(d.getListOrEmpty("problems"));
            if (d.getBooleanOr("rejoin", false)) MkToast.info(Component.translatable("makeium.dp.needs_rejoin"));
            if (problems.isEmpty()) MkToast.ok(Component.translatable("makeium.dp.valid"));
            else MkToast.error(Component.translatable("makeium.dp.invalid", problems.size()));
        });
        Net.sendToServer("dp/list");

        // sidebar navigation
        int y = TOP + 8;
        for (Page p : Page.values()) {
            add(new MkButton(0, y, sidebarWidth() - 1, 22, Component.translatable("makeium.dp.page." + p.name().toLowerCase(java.util.Locale.ROOT)), MkButton.Kind.NAV, () -> {
                page = p;
                rebuildWidgets();
            }).selected(page == p));
            y += 24;
        }
        add(new MkButton(6, height - 56, sidebarWidth() - 12, 20, Component.translatable("makeium.dp.open_pack"), MkButton.Kind.DEFAULT, this::openPackMenu));
        add(new MkButton(6, height - 32, sidebarWidth() - 12, 20, Component.translatable("makeium.dp.new_pack"), MkButton.Kind.DEFAULT, this::newPack));

        // top bar actions
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        add(new MkButton(width - PAD - 60 - 6 - 110, 4, 110, 20, Component.translatable("makeium.dp.save"), MkButton.Kind.PRIMARY, DatapackScreen::save));
        topActionsWidth = 60 + 6 + 110 + (page == Page.SCRIPTS ? 76 : 0) + PAD + MkTheme.mono().width(current.pack + " •") + 22;
        if (page == Page.SCRIPTS) {
            add(new MkButton(width - PAD - 60 - 6 - 110 - 6 - 70, 4, 70, 20, Component.translatable(codeShown() ? "makeium.dp.hide_code" : "makeium.dp.show_code"), MkButton.Kind.DEFAULT, () -> {
                showCode = !codeShown();
                rebuildWidgets();
            }));
        }

        switch (page) {
            case SCRIPTS -> buildScripts();
            case FILES -> buildFiles();
            case SETTINGS -> buildSettings();
        }
    }

    private boolean codeShown() {
        if (showCode == null) showCode = width >= 640;
        return showCode;
    }

    private int codeW() {
        return codeShown() ? Math.min(280, (width - sidebarWidth()) / 3) : 0;
    }

    private int problemsH() {
        return problems.isEmpty() ? 0 : Math.min(60, 14 + problems.size() * 11);
    }

    private void buildScripts() {
        int x = sidebarWidth(), y = TOP;
        editor = add(new BlockEditor(this, x, y, width - x - codeW(), height - y - problemsH(), current, () -> dirty = true));
    }

    // ---------------------------------------------------------------- files page

    private void buildFiles() {
        int x = sidebarWidth() + PAD, y = TOP + PAD;
        int listW = 220;
        add(new MkButton(x, y, listW, 20, Component.translatable("makeium.dp.new_file"), MkButton.Kind.PRIMARY, () -> openPopup(new NewFilePopup())));
        MkList list = add(new MkList(x, y + 26, listW, height - y - 26 - PAD - problemsH()));
        List<MkList.Item> rows = new ArrayList<>();
        Map<String, List<String>> byType = new LinkedHashMap<>();
        for (String path : current.files.keySet()) {
            String[] p = path.split("/");
            String type = p.length > 3 && p[0].equals("data") ? (p[2].equals("tags") && p.length > 4 ? "tags/" + p[3] : p[2]) : "other";
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(path);
        }
        for (Map.Entry<String, List<String>> e : byType.entrySet()) {
            rows.add(new MkList.Header(Component.literal(e.getKey())));
            for (String path : e.getValue()) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                rows.add(new MkList.Row(Component.literal(name), Component.literal(path).withColor(MkTheme.FAINT), null, () -> {
                    selectedFile = path;
                    rebuildWidgets();
                }).selected(path.equals(selectedFile)));
            }
        }
        if (rows.isEmpty()) rows.add(new MkList.Note(Component.translatable("makeium.dp.no_files"), MkTheme.FAINT));
        list.setItems(rows);

        if (selectedFile != null && current.files.containsKey(selectedFile)) {
            int ex = x + listW + PAD, ew = width - ex - PAD;
            int bh = height - (y + 26) - PAD - problemsH() - 28;
            fileBox = MultiLineEditBox.builder().setX(ex + 4).setY(y + 30).setShowBackground(false).setShowDecorations(true)
                    .setTextColor(MkTheme.TEXT).setTextShadow(false).setCursorColor(MkTheme.GREEN_HOVER).build(MkTheme.mono(), ew - 8, bh - 8, Component.empty());
            fileBox.setValue(current.files.get(selectedFile), true);
            fileBox.setValueListener(s -> {
                current.files.put(selectedFile, s);
                dirty = true;
            });
            add(fileBox);
            int by = y + 26 + bh + 4;
            add(new MkButton(ex, by, 90, 20, Component.translatable("makeium.dp.validate"), MkButton.Kind.DEFAULT, () -> {
                CompoundTag files = new CompoundTag();
                files.putString(selectedFile, current.files.get(selectedFile));
                CompoundTag d = new CompoundTag();
                d.put("files", files);
                Net.sendToServer("dp/validate", d);
            }));
            add(new MkButton(ex + 96, by, 90, 20, Component.translatable("makeium.dp.format"), MkButton.Kind.DEFAULT, () -> {
                try {
                    var json = com.google.gson.JsonParser.parseString(current.files.get(selectedFile));
                    String pretty = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json);
                    current.files.put(selectedFile, pretty + "\n");
                    dirty = true;
                    rebuildWidgets();
                } catch (RuntimeException ex2) {
                    MkToast.error(Component.literal(String.valueOf(ex2.getMessage())));
                }
            }));
            if (selectedFile.contains("/recipe/")) {
                add(new MkButton(ex + 192, by, 110, 20, Component.translatable("makeium.dp.recipe_grid"), MkButton.Kind.DEFAULT, () -> openPopup(new RecipeGridPopup(this, current.files.get(selectedFile), json -> {
                    current.files.put(selectedFile, json);
                    dirty = true;
                    rebuildWidgets();
                }))));
            }
            add(new MkButton(ex + ew - 80, by, 80, 20, Component.translatable("makeium.ui.delete"), MkButton.Kind.DANGER, () ->
                    MkDialog.confirm(this, Component.translatable("makeium.ui.delete"), Component.literal(selectedFile), () -> {
                        current.files.remove(selectedFile);
                        selectedFile = null;
                        dirty = true;
                        rebuildWidgets();
                    })));
        } else {
            fileBox = null;
        }
    }

    private final class NewFilePopup extends MkWidgetPopup {
        private final MkDropdown<String> type;
        private final MkField name;
        private final MkButton ok;

        NewFilePopup() {
            super((DatapackScreen.this.width - 300) / 2, (DatapackScreen.this.height - 130) / 2, 300, 130);
            type = child(new MkDropdown<>(DatapackScreen.this, 0, 0, w - 24, 18, JsonTemplates.types(registries), "recipe", Component::literal, v -> {}));
            name = child(new MkField(0, 0, w - 24, 18, Component.translatable("makeium.dp.file_name")));
            name.value("my_file");
            ok = child(new MkButton(0, 0, 90, 20, Component.translatable("makeium.ui.create"), MkButton.Kind.PRIMARY, this::create));
            focus(name);
        }

        @Override
        protected void layout() {
            type.setPosition(x + 12, y + 34);
            name.moveBox(x + 12, y + 70);
            ok.setPosition(x + w - 102, y + h - 28);
        }

        private void create() {
            String path = JsonTemplates.path(BlockCompiler.sanitize(current.namespace), type.value(), name.getValue());
            current.files.putIfAbsent(path, JsonTemplates.template(type.value()).replace("my_map:", BlockCompiler.sanitize(current.namespace) + ":"));
            selectedFile = path;
            dirty = true;
            close();
            rebuildWidgets();
        }

        @Override
        protected boolean onEnter() {
            create();
            return true;
        }

        @Override
        public boolean dims() {
            return true;
        }

        @Override
        protected void renderFrame(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            MkTheme.modal(g, x, y, w, h);
            MkTheme.boldText(g, Component.translatable("makeium.dp.new_file"), x + 12, y + 10, MkTheme.TEXT);
            MkTheme.text(g, Component.translatable("makeium.dp.file_type"), x + 12, y + 24, MkTheme.MUTED);
            MkTheme.text(g, Component.translatable("makeium.dp.file_name"), x + 12, y + 60, MkTheme.MUTED);
        }
    }

    // ---------------------------------------------------------------- settings page

    private void buildSettings() {
        int x = sidebarWidth() + PAD;
        form = new MkForm(this, w -> addRenderableWidget(w), x, TOP + PAD, Math.min(360, width - x - PAD));
        form.caption(Component.translatable("makeium.dp.pack_info"));
        form.field(Component.translatable("makeium.dp.pack_folder"), current.pack, s -> {
            current.pack = BlockCompiler.sanitize(s);
            dirty = true;
        });
        form.field(Component.translatable("makeium.dp.namespace"), current.namespace, s -> {
            current.namespace = BlockCompiler.sanitize(s);
            dirty = true;
        });
        form.field(Component.translatable("makeium.dp.description"), current.description, s -> {
            current.description = s;
            dirty = true;
        });
        form.caption(Component.translatable("makeium.dp.help"));
    }

    // ---------------------------------------------------------------- actions

    /** Function ids known in this project (for FUNCTION inputs). */
    public static List<String> functionNames() {
        List<String> out = new ArrayList<>();
        if (current == null) return out;
        String ns = BlockCompiler.sanitize(current.namespace);
        for (Script s : current.scripts) {
            Node h = s.head();
            if (h != null && h.def.equals("ev_function")) {
                String n = h.value("name", "");
                out.add(n.contains(":") ? n : ns + ":" + BlockCompiler.sanitizePath(n));
            }
        }
        for (String path : current.files.keySet()) {
            String prefix = "data/" + ns + "/function/";
            if (path.startsWith(prefix) && path.endsWith(".mcfunction")) out.add(ns + ":" + path.substring(prefix.length(), path.length() - 11));
        }
        return out;
    }

    public static void save() {
        BlockCompiler.Result r = BlockCompiler.compile(current);
        Map<String, String> all = current.allFilesForSave(r.files());
        List<String> deleted = new ArrayList<>();
        for (String old : current.generated) if (!r.files().containsKey(old) && !current.files.containsKey(old)) deleted.add(old);
        current.generated.clear();
        current.generated.addAll(r.files().keySet());
        all.put(Project.FILE, current.toJson());
        CompoundTag d = new CompoundTag();
        d.putString("name", current.pack);
        d.putString("description", current.description);
        d.put("files", ServerFeatures.toTag(all));
        ListTag del = new ListTag();
        for (String s : deleted) del.add(StringTag.valueOf(s));
        d.put("deleted", del);
        Net.sendToServer("dp/save", d);
        problems = new ArrayList<>(r.problems());
        dirty = false;
        MkToast.info(Component.translatable("makeium.dp.saving", current.pack));
    }

    private void openPackMenu() {
        if (packs.isEmpty()) {
            MkToast.info(Component.translatable("makeium.dp.no_packs"));
            Net.sendToServer("dp/list");
            return;
        }
        openPopup(new MkDropdown.ListPopup<>(6, height - 56 - Math.min(packs.size(), 14) * 14 - 6, 200, packs, current.pack, Component::literal, name -> {
            Runnable load = () -> {
                CompoundTag d = new CompoundTag();
                d.putString("name", name);
                Net.sendToServer("dp/load", d);
            };
            if (dirty) MkDialog.confirm(this, Component.translatable("makeium.dp.discard"), Component.translatable("makeium.dp.discard_text"), load);
            else load.run();
        }));
    }

    private void newPack() {
        MkDialog.prompt(this, Component.translatable("makeium.dp.new_pack"), Component.translatable("makeium.dp.pack_folder"), "new_pack", name -> {
            Project p = new Project();
            p.pack = BlockCompiler.sanitize(name);
            p.namespace = p.pack;
            Script s = new Script(20, 20);
            s.nodes.add(Node.create(BlockDefs.get("ev_load")));
            Node say = Node.create(BlockDefs.get("tellraw"));
            s.nodes.add(say);
            p.scripts.add(s);
            current = p;
            dirty = true;
            selectedFile = null;
            rebuildWidgets();
        });
    }

    /** Dev runs: a sample project for screenshots. */
    public static void devDemo() {
        Project p = new Project();
        p.pack = "demo_map";
        p.namespace = "demo_map";
        Script s = new Script(20, 20);
        s.nodes.add(Node.create(BlockDefs.get("ev_join")));
        Node title = Node.create(BlockDefs.get("title"));
        title.values.put("who", "@s");
        s.nodes.add(title);
        Node iff = Node.create(BlockDefs.get("if"));
        iff.values.put("cond", "entity @s[tag=!visited]");
        Node give = Node.create(BlockDefs.get("give"));
        give.values.put("item", "minecraft:map");
        iff.body.add(give);
        Node tag = Node.create(BlockDefs.get("tag_add"));
        tag.values.put("tag", "visited");
        iff.body.add(tag);
        s.nodes.add(iff);
        s.nodes.add(Node.create(BlockDefs.get("playsound")));
        p.scripts.add(s);
        Script s2 = new Script(20, 170);
        Node fn = Node.create(BlockDefs.get("ev_function"));
        fn.values.put("name", "open_door");
        s2.nodes.add(fn);
        Node rep = Node.create(BlockDefs.get("repeat"));
        rep.body.add(Node.create(BlockDefs.get("particle")));
        s2.nodes.add(rep);
        s2.nodes.add(Node.create(BlockDefs.get("wait")));
        s2.nodes.add(Node.create(BlockDefs.get("fill")));
        p.scripts.add(s2);
        current = p;
        dirty = true;
    }

    public static void devPage(String name) {
        page = Page.valueOf(name.toUpperCase(java.util.Locale.ROOT));
    }

    private static List<String> strings(ListTag l) {
        List<String> out = new ArrayList<>();
        for (Tag t : l) out.add(t.asString().orElse(""));
        return out;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void drawChrome(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.drawChrome(g, mouseX, mouseY);
        // pack name chip after the title
        String chip = current.pack + (dirty ? " •" : "");
        int tx = width - topActionsWidth - MkTheme.mono().width(chip) - 16;
        g.fill(tx, 7, tx + MkTheme.mono().width(chip) + 10, 21, MkTheme.SURFACE);
        MkTheme.monoText(g, chip, tx + 5, 10, dirty ? MkTheme.YELLOW : MkTheme.GREEN_TEXT);
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (page == Page.SETTINGS && form != null) {
            form.draw(g);
            MkTheme.paragraph(g, Component.translatable("makeium.dp.help.text", BlockCompiler.sanitize(current.namespace)), form.x, form.y, form.w, MkTheme.MUTED);
        }
        if (page == Page.FILES && fileBox != null) {
            int x = fileBox.getX() - 4, y = fileBox.getY() - 4;
            g.fill(x, y, x + fileBox.getWidth() + 8, y + fileBox.getHeight() + 8, MkTheme.INPUT);
            MkTheme.border(g, x, y, fileBox.getWidth() + 8, fileBox.getHeight() + 8, fileBox.isFocused() ? MkTheme.GREEN_HOVER : MkTheme.LINE);
        }
        if (page == Page.FILES && selectedFile == null) {
            MkTheme.textCentered(g, MkTheme.font(), Component.translatable("makeium.dp.pick_file"), (sidebarWidth() + 240 + width) / 2, height / 2, MkTheme.FAINT);
        }
    }

    @Override
    protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (page == Page.SCRIPTS && codeShown()) drawCode(g);
        drawProblems(g);
    }

    private void drawCode(GuiGraphicsExtractor g) {
        int x = width - codeW(), y = TOP, h = height - TOP - problemsH();
        g.fill(x, y, width, y + h, MkTheme.PANEL);
        MkTheme.vline(g, x, y, y + h, MkTheme.LINE);
        MkTheme.caption(g, Component.translatable("makeium.dp.code"), x + 8, y + 8);
        Script s = editor == null ? null : editor.selectedScript();
        if (s == null && !current.scripts.isEmpty()) s = current.scripts.get(0);
        if (s == null) {
            MkTheme.text(g, Component.translatable("makeium.dp.code_empty"), x + 8, y + 24, MkTheme.FAINT);
            return;
        }
        String code = BlockCompiler.preview(current, s);
        g.enableScissor(x + 1, y + 20, width, y + h);
        int ly = y + 24 - (int) codeScroll;
        int n = 1;
        for (String line : code.split("\n", -1)) {
            if (ly > y + h) break;
            if (ly > y + 10) {
                MkTheme.monoText(g, String.format("%3d", n), x + 4, ly, MkTheme.FAINT);
                int color = line.startsWith("#") ? 0xFF6A9955 : line.startsWith("execute") ? 0xFFC586C0 : MkTheme.TEXT;
                MkTheme.monoText(g, MkTheme.clip(MkTheme.mono(), line, codeW() - 34), x + 28, ly, color);
            }
            ly += 10;
            n++;
        }
        g.disableScissor();
    }

    private void drawProblems(GuiGraphicsExtractor g) {
        int ph = problemsH();
        if (ph == 0) return;
        int x = sidebarWidth(), y = height - ph;
        g.fill(x, y, width, height, 0xFF2A1615);
        MkTheme.hline(g, x, width, y, MkTheme.RED);
        MkTheme.text(g, Component.translatable("makeium.dp.problems", problems.size()), x + 8, y + 3, MkTheme.RED);
        int ly = y + 14;
        for (String p : problems) {
            if (ly > height - 10) break;
            MkTheme.monoText(g, MkTheme.clip(MkTheme.mono(), p, width - x - 16), x + 8, ly, 0xFFFFC0C0);
            ly += 11;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (page == Page.SCRIPTS && codeShown() && mx >= width - codeW() && !hasPopup()) {
            codeScroll = Math.max(0, codeScroll - sy * 20);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (!hasPopup() && e.hasControlDown() && e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_S) {
            save();
            return true;
        }
        return super.keyPressed(e);
    }
}
