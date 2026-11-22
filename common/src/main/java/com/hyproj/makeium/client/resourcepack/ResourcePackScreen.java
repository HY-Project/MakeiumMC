package com.hyproj.makeium.client.resourcepack;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyproj.makeium.client.feature.PackState;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkDialog;
import com.hyproj.makeium.client.ui.MkDropdown;
import com.hyproj.makeium.client.ui.MkField;
import com.hyproj.makeium.client.ui.MkForm;
import com.hyproj.makeium.client.ui.MkList;
import com.hyproj.makeium.client.ui.MkPicker;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.feature.datapack.BlockCompiler;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The Resource Pack Factory: create or open a pack in resourcepacks/, then retexture anything (texture browser + pixel
 * editor, PNG drag & drop), make custom items (item_model), font icons, sounds and translations, or edit any file.
 * Every save reloads resources so the result is in the world at once.
 */
public class ResourcePackScreen extends MkScreen {
    private enum Page { OVERVIEW, TEXTURES, ITEMS, FONTS, SOUNDS, LANG, FILES }

    private static final String[] TEX_FOLDERS = {"block", "item", "entity", "gui", "painting", "particle", "environment", "mob_effect", "font", "misc"};
    private static Page page = Page.OVERVIEW;
    private static String texFolder = "block";
    private static String texSearch = "";
    private static String selectedFile;
    private static String itemId = "my_item", itemBase = "minecraft:paper", itemName = "My Item", itemParent = "item/generated";

    private List<Identifier> textures = List.of();
    private double texScroll;
    private MultiLineEditBox fileBox;
    private MkForm form;

    public ResourcePackScreen(Screen parent) {
        super(Component.translatable("makeium.feature.resourcepack"), parent);
        if (RpProject.current() == null) {
            List<String> packs = RpProject.list();
            var mcInst = net.minecraft.client.Minecraft.getInstance();
            String world = mcInst.getSingleplayerServer() != null ? mcInst.getSingleplayerServer().getWorldData().getLevelName() : null;
            if (world != null && packs.contains(BlockCompiler.sanitize(world) + "_resources")) {
                try {
                    RpProject.open(BlockCompiler.sanitize(world) + "_resources");
                } catch (Exception e) {
                    RpProject.logFail("open", e);
                }
            }
        }
    }

    @Override
    protected int sidebarWidth() {
        return width < 600 ? 84 : 112;
    }

    @Override
    protected ScreenRectangle scrollArea() {
        return page == Page.ITEMS || page == Page.OVERVIEW ? new ScreenRectangle(sidebarWidth(), TOP, width - sidebarWidth(), height - TOP) : null;
    }

    public static void devPage(String name) {
        page = Page.valueOf(name.toUpperCase(Locale.ROOT));
    }

    private RpProject rp() {
        return RpProject.current();
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70 + 76;
        TextureCache.newFrame();
        int y = TOP + 8;
        for (Page p : Page.values()) {
            MkButton b = add(new MkButton(0, y, sidebarWidth() - 1, 22, Component.translatable("makeium.rp.page." + p.name().toLowerCase(Locale.ROOT)), MkButton.Kind.NAV, () -> {
                page = p;
                scroll = 0;
                rebuildWidgets();
            }).selected(page == p));
            b.active = p == Page.OVERVIEW || rp() != null;
            y += 24;
        }
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        if (rp() != null) add(new MkButton(width - PAD - 60 - 6 - 70, 4, 70, 20, Component.translatable("makeium.rp.apply"), MkButton.Kind.PRIMARY, () -> {
            rp().apply();
            TextureCache.clear();
            MkToast.ok(Component.translatable("makeium.rp.applied"));
        }));
        if (rp() == null) page = Page.OVERVIEW;
        switch (page) {
            case OVERVIEW -> buildOverview();
            case TEXTURES -> buildTextures();
            case ITEMS -> buildItems();
            case FONTS -> buildFonts();
            case SOUNDS -> buildSounds();
            case LANG -> buildLang();
            case FILES -> buildFiles();
        }
    }

    private int x0() {
        return sidebarWidth() + PAD;
    }

    private int contentW() {
        return width - sidebarWidth() - 2 * PAD;
    }

    // ---------------------------------------------------------------- overview

    private void buildOverview() {
        form = new MkForm(this, w -> addScrolled(w), x0(), TOP + 8 - scroll, Math.min(360, contentW() - 4));
        if (rp() != null) {
            form.caption(Component.translatable("makeium.rp.current", rp().name));
            form.field(Component.translatable("makeium.dp.description"), rp().description(), s -> {
                try {
                    rp().writeMeta(s);
                } catch (Exception e) {
                    RpProject.logFail("meta", e);
                }
            });
            form.buttons(MkButton.Kind.DEFAULT,
                    new Component[]{Component.translatable("makeium.rp.export_zip"), Component.translatable("makeium.rp.open_folder")},
                    new Runnable[]{() -> {
                        try {
                            Path p = rp().exportToDownloads();
                            MkToast.ok(Component.translatable("makeium.rp.exported", p.toString()));
                        } catch (Exception e) {
                            MkToast.error(Component.translatable("makeium.rp.export_failed", e.getMessage()));
                        }
                    }, () -> com.mojang.blaze3d.Blaze3D.openPath(rp().dir)});
            if (minecraft.getSingleplayerServer() != null) {
                form.button(Component.translatable("makeium.rp.world_pack"), MkButton.Kind.PRIMARY, () -> {
                    try {
                        rp().exportToWorld();
                        MkToast.ok(Component.translatable("makeium.rp.world_pack_done"));
                    } catch (Exception e) {
                        MkToast.error(Component.translatable("makeium.rp.export_failed", e.getMessage()));
                    }
                });
            }
        }
        form.caption(Component.translatable("makeium.rp.packs"));
        String suggested = minecraft.getSingleplayerServer() != null ? BlockCompiler.sanitize(minecraft.getSingleplayerServer().getWorldData().getLevelName()) + "_resources" : "my_pack";
        form.button(Component.translatable("makeium.rp.new"), MkButton.Kind.PRIMARY, () ->
                MkDialog.prompt(this, Component.translatable("makeium.rp.new"), Component.translatable("makeium.dp.pack_folder"), suggested, name -> openPack(BlockCompiler.sanitize(name))));
        for (String p : RpProject.list()) {
            form.add(new MkButton(form.x, form.y, form.w, 18, Component.literal(p), rp() != null && p.equals(rp().name) ? MkButton.Kind.PRIMARY : MkButton.Kind.DEFAULT, () -> openPack(p)));
            form.y += 21;
        }
    }

    private void openPack(String name) {
        try {
            RpProject.open(name).apply();
            TextureCache.clear();
            MkToast.ok(Component.translatable("makeium.rp.opened", name));
        } catch (Exception e) {
            MkToast.error(Component.translatable("makeium.rp.export_failed", e.getMessage()));
        }
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- textures

    private void buildTextures() {
        int x = x0();
        int fw = Math.min(150, contentW() / 3);
        add(new MkDropdown<>(this, x, TOP + 6, fw, 18, List.of(TEX_FOLDERS), texFolder, Component::literal, v -> {
            texFolder = v;
            texScroll = 0;
            rebuildWidgets();
        }));
        int searchW = width - PAD - 180 - 4 - (x + fw + 4);
        boolean narrow = searchW < 80;
        MkField search = add(new MkField(x + fw + 4, TOP + 6, narrow ? contentW() - fw - 4 : searchW, 18, Component.translatable("makeium.ui.search")));
        search.value(texSearch);
        search.onChange(s -> {
            texSearch = s;
            refilterTextures();
        });
        int by = narrow ? TOP + 28 : TOP + 6;
        add(new MkButton(width - PAD - 180, by, 88, 18, Component.translatable("makeium.rp.looked_block"), MkButton.Kind.DEFAULT, this::editLookedBlock));
        add(new MkButton(width - PAD - 88, by, 88, 18, Component.translatable("makeium.rp.held_item"), MkButton.Kind.DEFAULT, this::editHeldItem));
        refilterTextures();
    }

    private void refilterTextures() {
        String folder = texFolder.equals("misc") ? "textures/misc" : "textures/" + texFolder;
        Map<Identifier, Resource> found = minecraft.getResourceManager().listResources(folder, id -> id.getPath().endsWith(".png"));
        String f = texSearch.toLowerCase(Locale.ROOT).strip();
        textures = new ArrayList<>(new TreeMap<>(found).keySet().stream().filter(id -> f.isEmpty() || id.toString().contains(f)).toList());
    }

    private int texCell() {
        return 26;
    }

    private int texTop() {
        int fw = Math.min(150, contentW() / 3);
        boolean narrow = width - PAD - 180 - 4 - (x0() + fw + 4) < 80;
        return narrow ? TOP + 52 : TOP + 30;
    }

    private void editTexture(Identifier png) {
        try {
            Optional<Resource> res = minecraft.getResourceManager().getResource(png);
            NativeImage img;
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    img = NativeImage.read(in);
                }
            } else {
                img = PixelEditorScreen.blank(16, 16);
            }
            String target = "assets/" + png.getNamespace() + "/" + png.getPath();
            minecraft.gui.setScreen(new PixelEditorScreen(this, rp(), target, img, () -> TextureCache.invalidate(png)));
        } catch (Exception e) {
            MkToast.error(Component.translatable("makeium.px.import_failed", e.getMessage()));
        }
    }

    private void editLookedBlock() {
        if (minecraft.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && minecraft.level != null) {
            Block b = minecraft.level.getBlockState(bh.getBlockPos()).getBlock();
            Identifier id = BuiltInRegistries.BLOCK.getKey(b);
            for (String suffix : new String[]{"", "_side", "_top", "_front", "_0"}) {
                Identifier png = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/block/" + id.getPath() + suffix + ".png");
                if (minecraft.getResourceManager().getResource(png).isPresent()) {
                    editTexture(png);
                    return;
                }
            }
            texFolder = "block";
            texSearch = id.getPath();
            rebuildWidgets();
        }
    }

    private void editHeldItem() {
        if (minecraft.player == null || minecraft.player.getMainHandItem().isEmpty()) return;
        Item item = minecraft.player.getMainHandItem().getItem();
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        for (String folder : new String[]{"item", "block"}) {
            Identifier png = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + folder + "/" + id.getPath() + ".png");
            if (minecraft.getResourceManager().getResource(png).isPresent()) {
                editTexture(png);
                return;
            }
        }
        texFolder = "item";
        texSearch = id.getPath();
        rebuildWidgets();
    }

    private void drawTextures(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        TextureCache.newFrame();
        int x = x0(), y = texTop(), c = texCell();
        int cols = Math.max(1, contentW() / c);
        int rows = (height - y - PAD) / c;
        int start = (int) texScroll * cols;
        Identifier hover = null;
        for (int i = 0; i < cols * rows; i++) {
            int idx = start + i;
            if (idx >= textures.size()) break;
            Identifier id = textures.get(idx);
            int cx = x + (i % cols) * c, cy = y + (i / cols) * c;
            boolean hot = mouseX >= cx && mouseX < cx + c - 2 && mouseY >= cy && mouseY < cy + c - 2;
            g.fill(cx, cy, cx + c - 2, cy + c - 2, hot ? MkTheme.SURFACE_HOVER : MkTheme.SURFACE);
            TextureCache.Thumb t = TextureCache.get(id);
            if (t != null) TextureCache.draw(g, t, cx + 2, cy + 2, c - 6);
            boolean overridden = rp() != null && rp().exists("assets/" + id.getNamespace() + "/" + id.getPath());
            if (overridden) g.fill(cx + c - 7, cy + 1, cx + c - 3, cy + 5, MkTheme.GREEN_HOVER);
            if (hot) hover = id;
        }
        MkTheme.text(g, Component.translatable("makeium.ui.count", textures.size()), x, height - 12, MkTheme.FAINT);
        if (hover != null) g.setTooltipForNextFrame(MkTheme.font(), Component.literal(hover.getPath().replace("textures/", "")), mouseX, mouseY);
    }

    // ---------------------------------------------------------------- custom items

    private void buildItems() {
        form = new MkForm(this, w -> addScrolled(w), x0(), TOP + 8 - scroll, Math.min(340, contentW() - 4));
        form.caption(Component.translatable("makeium.rp.item_new"));
        form.field(Component.translatable("makeium.rp.item_id"), itemId, s -> itemId = BlockCompiler.sanitizePath(s).replace('/', '_'));
        form.field(Component.translatable("makeium.rp.item_name"), itemName, s -> itemName = s);
        MkField base = form.field(Component.translatable("makeium.rp.item_base"), itemBase, s -> itemBase = s.strip());
        form.button(Component.translatable("makeium.display.pick_item"), MkButton.Kind.DEFAULT, () -> openPopup(new MkPicker(this, Component.translatable("makeium.display.pick_item"), MkPicker.items(), id -> {
            itemBase = id.toString();
            base.value(itemBase);
        })));
        form.dropdown(Component.translatable("makeium.rp.item_parent"), List.of("item/generated", "item/handheld", "block/cube_all"), itemParent,
                v -> Component.translatable("makeium.rp.parent." + v.replace('/', '_')), v -> itemParent = v);
        form.buttons(MkButton.Kind.PRIMARY,
                new Component[]{Component.translatable("makeium.rp.item_draw"), Component.translatable("makeium.rp.item_save")},
                new Runnable[]{this::drawItemTexture, this::saveItem});
        form.button(Component.translatable("makeium.rp.item_give"), MkButton.Kind.DEFAULT, () -> {
            String cmd = "give @s " + itemBase + "[item_model=\"" + rp().ns + ":" + itemId + "\",item_name=" + new com.google.gson.JsonPrimitive(itemName) + "]";
            PackState.runCommand(cmd);
            minecraft.keyboardHandler.setClipboard("/" + cmd);
            MkToast.ok(Component.translatable("makeium.rp.item_given"));
        });
        form.caption(Component.translatable("makeium.rp.items"));
        for (String f : rp().files()) {
            String prefix = "assets/" + rp().ns + "/items/";
            if (f.startsWith(prefix) && f.endsWith(".json")) {
                String id = f.substring(prefix.length(), f.length() - 5);
                form.add(new MkButton(form.x, form.y, form.w, 18, Component.literal(rp().ns + ":" + id), MkButton.Kind.DEFAULT, () -> {
                    itemId = id;
                    rebuildWidgets();
                }));
                form.y += 21;
            }
        }
    }

    private String itemTexture() {
        return "assets/" + rp().ns + "/textures/item/" + itemId + ".png";
    }

    private void drawItemTexture() {
        try {
            NativeImage img;
            Path existing = rp().file(itemTexture());
            if (Files.exists(existing)) {
                try (InputStream in = Files.newInputStream(existing)) {
                    img = NativeImage.read(in);
                }
            } else {
                img = PixelEditorScreen.blank(16, 16);
            }
            minecraft.gui.setScreen(new PixelEditorScreen(this, rp(), itemTexture(), img, this::saveItem));
        } catch (Exception e) {
            MkToast.error(Component.literal(String.valueOf(e.getMessage())));
        }
    }

    /** items/&lt;id&gt;.json (item model definition) + models/item/&lt;id&gt;.json, pointing at the texture. */
    /** Dev runs: create a custom item the way the Items page does. */
    public static void devItem(String id, String base, String name) {
        itemId = id;
        itemBase = base;
        itemName = name;
        new ResourcePackScreen(null).saveItem();
    }

    private void saveItem() {
        try {
            String ns = rp().ns;
            JsonObject def = new JsonObject();
            JsonObject model = new JsonObject();
            model.addProperty("type", "minecraft:model");
            model.addProperty("model", ns + ":item/" + itemId);
            def.add("model", model);
            rp().writeText("assets/" + ns + "/items/" + itemId + ".json", pretty(def));
            JsonObject m = new JsonObject();
            JsonObject tex = new JsonObject();
            if (itemParent.equals("block/cube_all")) {
                m.addProperty("parent", "minecraft:block/cube_all");
                tex.addProperty("all", ns + ":item/" + itemId);
            } else {
                m.addProperty("parent", "minecraft:" + itemParent);
                tex.addProperty("layer0", ns + ":item/" + itemId);
            }
            m.add("textures", tex);
            rp().writeText("assets/" + ns + "/models/item/" + itemId + ".json", pretty(m));
            if (!rp().exists(itemTexture())) {
                NativeImage img = PixelEditorScreen.blank(16, 16);
                for (int i = 0; i < 16; i++) img.setPixel(i, i, 0xFF52A535);
                rp().writeImage(itemTexture(), img);
                img.close();
            }
            rp().apply();
            MkToast.ok(Component.translatable("makeium.rp.item_saved", ns + ":" + itemId));
        } catch (Exception e) {
            MkToast.error(Component.literal(String.valueOf(e.getMessage())));
        }
    }

    private static String pretty(JsonElement e) {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(e) + "\n";
    }

    // ---------------------------------------------------------------- font icons

    private String fontFile() {
        return "assets/" + rp().ns + "/font/icons.json";
    }

    private JsonObject fontJson() {
        String txt = rp().readText(fontFile());
        try {
            if (txt != null) return JsonParser.parseString(txt).getAsJsonObject();
        } catch (RuntimeException ignored) {
            // rebuild
        }
        JsonObject o = new JsonObject();
        o.add("providers", new JsonArray());
        return o;
    }

    private void buildFonts() {
        int x = x0();
        add(new MkButton(x, TOP + 6, 140, 20, Component.translatable("makeium.rp.icon_new"), MkButton.Kind.PRIMARY, () ->
                MkDialog.prompt(this, Component.translatable("makeium.rp.icon_new"), Component.translatable("makeium.rp.icon_name"), "heart", n -> {
                    String name = BlockCompiler.sanitizePath(n).replace('/', '_');
                    JsonObject font = fontJson();
                    JsonArray providers = font.getAsJsonArray("providers");
                    int code = 0xE000 + providers.size();
                    JsonObject p = new JsonObject();
                    p.addProperty("type", "bitmap");
                    p.addProperty("file", rp().ns + ":font/" + name + ".png");
                    p.addProperty("ascent", 7);
                    p.addProperty("height", 8);
                    JsonArray chars = new JsonArray();
                    chars.add(String.valueOf((char) code));
                    p.add("chars", chars);
                    providers.add(p);
                    try {
                        rp().writeText(fontFile(), pretty(font));
                        NativeImage img = PixelEditorScreen.blank(8, 8);
                        minecraft.gui.setScreen(new PixelEditorScreen(this, rp(), "assets/" + rp().ns + "/textures/font/" + name + ".png", img, () -> {}));
                    } catch (Exception e) {
                        MkToast.error(Component.literal(String.valueOf(e.getMessage())));
                    }
                })));
        MkList list = add(new MkList(x, TOP + 32, contentW(), height - TOP - 32 - PAD));
        List<MkList.Item> rows = new ArrayList<>();
        for (JsonElement e : fontJson().getAsJsonArray("providers")) {
            if (!e.isJsonObject()) continue;
            JsonObject p = e.getAsJsonObject();
            String file = p.has("file") ? p.get("file").getAsString() : "";
            String ch = p.has("chars") && !p.getAsJsonArray("chars").isEmpty() ? p.getAsJsonArray("chars").get(0).getAsString() : "";
            String code = ch.isEmpty() ? "" : String.format("\\u%04X", (int) ch.charAt(0));
            String json = "{\"text\":\"" + code + "\",\"font\":\"" + rp().ns + ":icons\"}";
            rows.add(new MkList.Row(Component.literal(file), Component.literal(code + "   " + json), Component.translatable("makeium.rp.copy"), () -> {
                minecraft.keyboardHandler.setClipboard(json);
                MkToast.ok(Component.translatable("makeium.display.copied"));
            }));
        }
        if (rows.isEmpty()) rows.add(new MkList.Note(Component.translatable("makeium.rp.icons_none"), MkTheme.FAINT));
        list.setItems(rows);
    }

    // ---------------------------------------------------------------- sounds

    private String soundsFile() {
        return "assets/" + rp().ns + "/sounds.json";
    }

    private JsonObject soundsJson() {
        String txt = rp().readText(soundsFile());
        try {
            if (txt != null) return JsonParser.parseString(txt).getAsJsonObject();
        } catch (RuntimeException ignored) {
            // new
        }
        return new JsonObject();
    }

    private void buildSounds() {
        int x = x0();
        MkList list = add(new MkList(x, TOP + 30, contentW(), height - TOP - 30 - PAD));
        List<MkList.Item> rows = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : soundsJson().entrySet()) {
            String id = rp().ns + ":" + e.getKey();
            rows.add(new MkList.Row(Component.literal(id), Component.literal("playsound " + id + " master @a"), Component.translatable("makeium.rp.play"), () -> {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(Identifier.parse(id)), 1f));
                minecraft.keyboardHandler.setClipboard("/playsound " + id + " master @a");
            }));
        }
        if (rows.isEmpty()) rows.add(new MkList.Note(Component.translatable("makeium.rp.sounds_none"), MkTheme.FAINT));
        list.setItems(rows);
    }

    // ---------------------------------------------------------------- language

    private Map<String, String> lang(String code) {
        Map<String, String> out = new LinkedHashMap<>();
        String txt = rp().readText("assets/" + rp().ns + "/lang/" + code + ".json");
        try {
            if (txt != null) for (Map.Entry<String, JsonElement> e : JsonParser.parseString(txt).getAsJsonObject().entrySet()) out.put(e.getKey(), e.getValue().getAsString());
        } catch (RuntimeException ignored) {
            // empty
        }
        return out;
    }

    private void writeLang(String code, Map<String, String> m) {
        JsonObject o = new JsonObject();
        m.forEach(o::addProperty);
        try {
            rp().writeText("assets/" + rp().ns + "/lang/" + code + ".json", pretty(o));
        } catch (Exception e) {
            RpProject.logFail("lang", e);
        }
    }

    private void buildLang() {
        int x = x0();
        Map<String, String> en = lang("en_us"), ko = lang("ko_kr");
        add(new MkButton(x, TOP + 6, 120, 20, Component.translatable("makeium.rp.lang_add"), MkButton.Kind.PRIMARY, () ->
                MkDialog.prompt(this, Component.translatable("makeium.rp.lang_add"), Component.translatable("makeium.rp.lang_key"), "item." + rp().ns + ".my_item", key -> {
                    en.putIfAbsent(key, key);
                    ko.putIfAbsent(key, key);
                    writeLang("en_us", en);
                    writeLang("ko_kr", ko);
                    rebuildWidgets();
                })));
        MkList list = add(new MkList(x, TOP + 32, contentW(), height - TOP - 32 - PAD));
        List<MkList.Item> rows = new ArrayList<>();
        java.util.Set<String> keys = new java.util.TreeSet<>(en.keySet());
        keys.addAll(ko.keySet());
        for (String k : keys) {
            rows.add(new MkList.Row(Component.literal(k), Component.literal("EN " + en.getOrDefault(k, "") + "   ·   KO " + ko.getOrDefault(k, "")), null, () ->
                    MkDialog.prompt(this, Component.literal(k + " (en_us)"), Component.empty(), en.getOrDefault(k, ""), v -> {
                        en.put(k, v);
                        writeLang("en_us", en);
                        MkDialog.prompt(this, Component.literal(k + " (ko_kr)"), Component.empty(), ko.getOrDefault(k, ""), v2 -> {
                            ko.put(k, v2);
                            writeLang("ko_kr", ko);
                            rebuildWidgets();
                        });
                    })));
        }
        if (rows.isEmpty()) rows.add(new MkList.Note(Component.translatable("makeium.rp.lang_none"), MkTheme.FAINT));
        list.setItems(rows);
    }

    // ---------------------------------------------------------------- files

    private void buildFiles() {
        int x = x0(), lw = Math.min(200, contentW() / 3);
        MkList list = add(new MkList(x, TOP + 6, lw, height - TOP - 6 - PAD));
        List<MkList.Item> rows = new ArrayList<>();
        for (String f : rp().files()) {
            rows.add(new MkList.Row(Component.literal(f.substring(f.lastIndexOf('/') + 1)), Component.literal(f).withColor(MkTheme.FAINT), null, () -> {
                selectedFile = f;
                rebuildWidgets();
            }).selected(f.equals(selectedFile)));
        }
        list.setItems(rows);
        fileBox = null;
        if (selectedFile != null && (selectedFile.endsWith(".json") || selectedFile.endsWith(".mcmeta") || selectedFile.endsWith(".txt"))) {
            int ex = x + lw + PAD, ew = width - ex - PAD;
            fileBox = MultiLineEditBox.builder().setX(ex + 4).setY(TOP + 10).setShowBackground(false).setShowDecorations(true)
                    .setTextColor(MkTheme.TEXT).setTextShadow(false).setCursorColor(MkTheme.GREEN_HOVER).build(MkTheme.mono(), ew - 8, height - TOP - 44 - PAD, Component.empty());
            String txt = rp().readText(selectedFile);
            fileBox.setValue(txt == null ? "" : txt, true);
            add(fileBox);
            add(new MkButton(ex, height - PAD - 22, 90, 20, Component.translatable("makeium.ui.save"), MkButton.Kind.PRIMARY, () -> {
                try {
                    rp().writeText(selectedFile, fileBox.getValue());
                    rp().apply();
                    MkToast.ok(Component.translatable("makeium.px.saved", selectedFile));
                } catch (Exception e) {
                    MkToast.error(Component.literal(String.valueOf(e.getMessage())));
                }
            }));
            add(new MkButton(ex + ew - 80, height - PAD - 22, 80, 20, Component.translatable("makeium.ui.delete"), MkButton.Kind.DANGER, () ->
                    MkDialog.confirm(this, Component.translatable("makeium.ui.delete"), Component.literal(selectedFile), () -> {
                        try {
                            rp().delete(selectedFile);
                        } catch (Exception ignored) {
                            // gone
                        }
                        selectedFile = null;
                        rebuildWidgets();
                    })));
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (page == Page.TEXTURES && !hasPopup() && e.y() >= texTop()) {
            int c = texCell(), cols = Math.max(1, contentW() / c);
            int col = (int) ((e.x() - x0()) / c), row = (int) ((e.y() - texTop()) / c);
            if (e.x() >= x0() && col < cols) {
                int idx = (int) texScroll * cols + row * cols + col;
                if (idx >= 0 && idx < textures.size()) {
                    editTexture(textures.get(idx));
                    return true;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (page == Page.TEXTURES && !hasPopup() && my >= texTop()) {
            int c = texCell(), cols = Math.max(1, contentW() / c), rows = (height - texTop() - PAD) / c;
            int maxRow = Math.max(0, (textures.size() + cols - 1) / cols - rows);
            texScroll = Math.max(0, Math.min(maxRow, texScroll - Math.signum(sy) * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    /** Dropping files: PNGs on the items page become the item texture; .ogg files on the sounds page become sounds. */
    @Override
    public void onFilesDrop(List<Path> files) {
        if (rp() == null) return;
        try {
            if (page == Page.SOUNDS) {
                JsonObject sounds = soundsJson();
                for (Path f : files) {
                    String fn = f.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!fn.endsWith(".ogg")) continue;
                    String name = BlockCompiler.sanitizePath(fn.substring(0, fn.length() - 4)).replace('/', '_');
                    rp().copyIn(f, "assets/" + rp().ns + "/sounds/" + name + ".ogg");
                    JsonObject entry = new JsonObject();
                    JsonArray list = new JsonArray();
                    list.add(rp().ns + ":" + name);
                    entry.add("sounds", list);
                    sounds.add(name, entry);
                }
                rp().writeText(soundsFile(), pretty(sounds));
                rp().apply();
                rebuildWidgets();
            } else if (page == Page.ITEMS) {
                for (Path f : files) {
                    if (!f.toString().toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                    rp().copyIn(f, itemTexture());
                    saveItem();
                    break;
                }
            } else {
                MkToast.info(Component.translatable("makeium.rp.drop_hint"));
            }
        } catch (Exception e) {
            MkToast.error(Component.literal(String.valueOf(e.getMessage())));
        }
    }

    @Override
    public void removed() {
        TextureCache.clear();
        super.removed();
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        switch (page) {
            case OVERVIEW -> clipToScrollArea(g, () -> {
                form.draw(g);
                MkTheme.paragraph(g, Component.translatable("makeium.rp.intro"), form.x, form.y + 8, form.w, MkTheme.FAINT);
            });
            case ITEMS -> clipToScrollArea(g, () -> {
                form.draw(g);
                MkTheme.paragraph(g, Component.translatable("makeium.rp.items_help"), form.x, form.y + 8, form.w, MkTheme.FAINT);
            });
            case TEXTURES -> drawTextures(g, mouseX, mouseY);
            case SOUNDS -> MkTheme.text(g, MkTheme.clip(MkTheme.font(), Component.translatable("makeium.rp.sounds_help"), contentW()), x0(), TOP + 12, MkTheme.MUTED);
            case FILES -> {
                if (fileBox != null) {
                    int bx = fileBox.getX() - 4, by = fileBox.getY() - 4;
                    g.fill(bx, by, bx + fileBox.getWidth() + 8, by + fileBox.getHeight() + 8, MkTheme.INPUT);
                    MkTheme.border(g, bx, by, fileBox.getWidth() + 8, fileBox.getHeight() + 8, MkTheme.LINE);
                }
            }
            default -> {
            }
        }
    }
}
