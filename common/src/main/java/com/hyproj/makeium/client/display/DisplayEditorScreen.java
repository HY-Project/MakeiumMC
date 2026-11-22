package com.hyproj.makeium.client.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkField;
import com.hyproj.makeium.client.ui.MkForm;
import com.hyproj.makeium.client.ui.MkList;
import com.hyproj.makeium.client.ui.MkNumberField;
import com.hyproj.makeium.client.ui.MkPicker;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.feature.display.DisplayData;
import com.hyproj.makeium.feature.display.RichText;
import com.hyproj.makeium.feature.display.WallSnap;
import com.hyproj.makeium.net.Net;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Vector3f;

/**
 * The display editor. The world stays visible behind it and every change is applied to the real entity a few ticks
 * later, so what you see in the world is the result. Tabs: content (text segments / block / item), look, transform,
 * animation (interpolation + keyframes → datapack function).
 */
public class DisplayEditorScreen extends MkScreen {
    private enum Tab { CONTENT, LOOK, TRANSFORM, ANIMATION }

    private static final String[] BILLBOARDS = {"fixed", "vertical", "horizontal", "center"};
    private static final String[] ALIGNS = {"center", "left", "right"};
    private static final String[] ITEM_MODES = {"none", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand",
            "firstperson_righthand", "head", "gui", "ground", "fixed", "on_shelf"};

    private final String uuid;
    private final DisplayData data;
    /** Text segments (TEXT only). */
    private final List<RichText.Segment> segments;
    private int selectedSegment;
    private Tab tab = Tab.CONTENT;
    private boolean dirty;
    private int sinceSend;
    /** Keep the text centred on its wall point when its height changes (placed by wall snap). */
    private boolean autoCenter = true;
    private int lastLines = -1;
    private MkForm form;
    private MultiLineEditBox textBox;
    private MkList segmentList;

    // animation
    private static final class Key {
        Vector3f t, l, s, r;
        int ticks = 20;
    }

    private final List<Key> keys = new ArrayList<>();
    private String animName = "anim";
    private boolean animLoop;
    private int playIndex = -1, playWait;

    public DisplayEditorScreen(Screen parent, String uuid, DisplayData data) {
        super(Component.translatable("makeium.display.editor"), parent);
        this.uuid = uuid;
        this.data = data;
        this.segments = data.kind == WallSnap.Kind.TEXT ? RichText.fromComponent(data.text(regs())) : new ArrayList<>();
        if (segments.isEmpty() && data.kind == WallSnap.Kind.TEXT) segments.add(new RichText.Segment(""));
        // Drop identity/position keys: the server keeps the entity's own.
        for (String k : new String[]{"UUID", "Pos", "Rotation", "Motion", "id"}) data.tag.remove(k);
        if (data.kind == WallSnap.Kind.TEXT) lastLines = lineCount();
    }

    private HolderLookup.Provider regs() {
        return minecraft != null && minecraft.level != null ? minecraft.level.registryAccess()
                : net.minecraft.client.Minecraft.getInstance().level == null ? null : net.minecraft.client.Minecraft.getInstance().level.registryAccess();
    }

    @Override
    protected boolean seeThrough() {
        return true;
    }

    private int panelW() {
        return Math.min(300, Math.max(220, width * 2 / 5));
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        textBox = null;
        segmentList = null;
        int pw = panelW();
        // top-right actions
        int bw = width < 520 ? 64 : 96;
        add(new MkButton(width - PAD - 56, 4, 56, 20, Component.translatable("makeium.ui.done"), MkButton.Kind.PRIMARY, this::onClose));
        add(new MkButton(width - PAD - 56 - 4 - bw, 4, bw, 20, Component.translatable("makeium.display.copy_summon"), MkButton.Kind.DEFAULT, this::copySummon));
        add(new MkButton(width - PAD - 56 - 8 - 2 * bw, 4, bw, 20, Component.translatable("makeium.display.as_template"), MkButton.Kind.DEFAULT, () -> {
            DisplayBrush.setTemplate(data);
            MkToast.ok(Component.translatable("makeium.display.template_saved"));
        }));
        topActionsWidth = 56 + 8 + 2 * bw + PAD;
        // tabs
        Tab[] tabs = Tab.values();
        int tw = pw / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            add(new MkButton(i * tw, TOP, tw, 22, tabLabel(t), MkButton.Kind.TAB, () -> {
                tab = t;
                scroll = 0;
                rebuild();
            }).selected(t == tab));
        }
        form = new MkForm(this, w -> addScrolled(w), PAD, TOP + 30 - scroll, pw - 2 * PAD);
        switch (tab) {
            case CONTENT -> {
                switch (data.kind) {
                    case TEXT -> buildText();
                    case BLOCK -> buildBlock();
                    case ITEM -> buildItem();
                }
            }
            case LOOK -> buildLook();
            case TRANSFORM -> buildTransform();
            case ANIMATION -> buildAnimation();
        }
        add(new MkButton(PAD, height - 28, pw - 2 * PAD, 20, Component.translatable("makeium.display.delete"), MkButton.Kind.DANGER, () -> {
            CompoundTag d = new CompoundTag();
            d.putString("uuid", uuid);
            Net.sendToServer("display/remove", d);
            minecraft.gui.setScreen(parent);
        }));
    }

    @Override
    protected net.minecraft.client.gui.navigation.ScreenRectangle scrollArea() {
        return new net.minecraft.client.gui.navigation.ScreenRectangle(0, TOP + 23, panelW(), height - TOP - 23 - 32);
    }

    @Override
    protected void onScrolled() {
        rebuild();
    }

    private Component tabLabel(Tab t) {
        if (t == Tab.CONTENT) return Component.translatable("makeium.display.tab.content." + DisplayData.typeName(data.kind));
        return Component.translatable("makeium.display.tab." + t.name().toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- content: text

    private void buildText() {
        int pw = panelW();
        form.caption(Component.translatable("makeium.display.segments"));
        int listH = 64;
        segmentList = form.add(new MkList(form.x, form.y, form.w - 56, listH));
        refreshSegments();
        int bx = form.x + form.w - 52;
        form.add(new MkButton(bx, form.y, 52, 15, Component.literal("+"), MkButton.Kind.DEFAULT, () -> {
            RichText.Segment s = segments.get(selectedSegment).copy();
            s.text = " ";
            segments.add(selectedSegment + 1, s);
            selectedSegment++;
            changedText();
            rebuild();
        }));
        form.add(new MkButton(bx, form.y + 16, 52, 15, Component.literal("−"), MkButton.Kind.DEFAULT, () -> {
            if (segments.size() > 1) {
                segments.remove(selectedSegment);
                selectedSegment = Math.max(0, selectedSegment - 1);
                changedText();
                rebuild();
            }
        }));
        form.add(new MkButton(bx, form.y + 32, 25, 15, Component.literal("↑"), MkButton.Kind.DEFAULT, () -> moveSegment(-1)));
        form.add(new MkButton(bx + 27, form.y + 32, 25, 15, Component.literal("↓"), MkButton.Kind.DEFAULT, () -> moveSegment(1)));
        form.y += listH + 6;

        RichText.Segment seg = segments.get(Math.min(selectedSegment, segments.size() - 1));
        form.label(Component.translatable("makeium.display.segment_text"));
        textBox = MultiLineEditBox.builder().setX(form.x).setY(form.y).setShowBackground(false).setShowDecorations(false)
                .setTextColor(MkTheme.TEXT).setTextShadow(false).setCursorColor(MkTheme.GREEN_HOVER)
                .setPlaceholder(Component.translatable("makeium.display.segment_hint").withColor(MkTheme.FAINT))
                .build(MkTheme.font(), form.w, 54, Component.empty());
        textBox.setValue(seg.text);
        textBox.setValueListener(s -> {
            seg.text = s;
            changedText();
            refreshSegments();
        });
        form.add(textBox);
        form.y += 60;

        form.color(Component.translatable("makeium.display.color"), seg.color < 0 ? 0xFFFFFFFF : 0xFF000000 | seg.color, false, c -> {
            seg.color = c & 0xFFFFFF;
            changedText();
            refreshSegments();
        });
        form.buttons(MkButton.Kind.CHIP,
                new Component[]{Component.literal("B").withStyle(s -> s.withBold(true)), Component.literal("I").withStyle(s -> s.withItalic(true)),
                        Component.literal("U").withStyle(s -> s.withUnderlined(true)), Component.literal("S").withStyle(s -> s.withStrikethrough(true)),
                        Component.literal("?"), Component.translatable("makeium.display.no_color")},
                new Runnable[]{
                        () -> toggleStyle(seg, 0), () -> toggleStyle(seg, 1), () -> toggleStyle(seg, 2), () -> toggleStyle(seg, 3), () -> toggleStyle(seg, 4),
                        () -> {
                            seg.color = -1;
                            changedText();
                            rebuild();
                        }});
        // mark the active decorations
        List<MkButton> chips = new ArrayList<>();
        for (var c : children()) if (c instanceof MkButton b && b.getY() == form.y - 24) chips.add(b);
        boolean[] on = {seg.bold, seg.italic, seg.underlined, seg.strikethrough, seg.obfuscated};
        for (int i = 0; i < Math.min(5, chips.size()); i++) chips.get(i).selected(on[i]);

        List<Identifier> fonts = FontList.all();
        Identifier cur = seg.font == null ? Identifier.withDefaultNamespace("default") : seg.font;
        if (!fonts.contains(cur)) fonts.add(cur);
        form.dropdown(Component.translatable("makeium.display.font"), fonts, cur, id -> Component.literal(fontLabel(id)), id -> {
            seg.font = id.equals(Identifier.withDefaultNamespace("default")) ? null : id;
            changedText();
            refreshSegments();
        });
        form.toggle(Component.translatable("makeium.display.auto_center"), autoCenter, v -> autoCenter = v);
    }

    private static String fontLabel(Identifier id) {
        if (id.getNamespace().equals("minecraft")) {
            return switch (id.getPath()) {
                case "default" -> "minecraft:default (기본 / Default)";
                case "uniform" -> "minecraft:uniform (유니폼 / Uniform)";
                case "alt" -> "minecraft:alt (마법부여 / Enchanting)";
                case "illageralt" -> "minecraft:illageralt (일리저 / Illager)";
                default -> id.toString();
            };
        }
        return id.toString();
    }

    private void toggleStyle(RichText.Segment seg, int which) {
        switch (which) {
            case 0 -> seg.bold = !seg.bold;
            case 1 -> seg.italic = !seg.italic;
            case 2 -> seg.underlined = !seg.underlined;
            case 3 -> seg.strikethrough = !seg.strikethrough;
            default -> seg.obfuscated = !seg.obfuscated;
        }
        changedText();
        rebuild();
    }

    private void moveSegment(int dir) {
        int to = selectedSegment + dir;
        if (to < 0 || to >= segments.size()) return;
        RichText.Segment s = segments.remove(selectedSegment);
        segments.add(to, s);
        selectedSegment = to;
        changedText();
        rebuild();
    }

    private void refreshSegments() {
        if (segmentList == null) return;
        List<MkList.Item> rows = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            final int idx = i;
            RichText.Segment s = segments.get(i);
            String preview = s.text.replace("\n", "⏎");
            if (preview.isBlank()) preview = "(" + Component.translatable("makeium.display.empty").getString() + ")";
            Component title = Component.literal(preview).withStyle(s.style().withFont(net.minecraft.network.chat.FontDescription.DEFAULT));
            rows.add(new MkList.Row(title, null, Component.literal(s.font == null ? "" : s.font.getPath()), () -> {
                selectedSegment = idx;
                rebuild();
            }).selected(i == selectedSegment));
        }
        segmentList.setItems(rows);
    }

    private void changedText() {
        data.setText(RichText.toComponent(segments), regs());
        if (autoCenter) recenter();
        dirty = true;
    }

    private int lineCount() {
        return DisplayPicker.lineCount(data.text(regs()), data.getInt("line_width", 200));
    }

    /** Re-centres the text on its wall point after its line count or Y scale changed. */
    private void recenter() {
        if (data.kind != WallSnap.Kind.TEXT) return;
        int lines = lineCount();
        Vector3f t = data.translation();
        float roll = data.leftEuler().z;
        double hh = (WallSnap.textHeight(lines) + 1) / 2.0 * WallSnap.TEXT_PIXEL * data.scale().y;
        double r = Math.toRadians(roll);
        data.setTranslation(new Vector3f((float) (hh * Math.sin(r)), (float) (-hh * Math.cos(r)), t.z));
        lastLines = lines;
    }

    // ---------------------------------------------------------------- content: block / item

    private void buildBlock() {
        CompoundTag stateTag = data.tag.getCompoundOrEmpty("block_state");
        String name = stateTag.getStringOr("Name", "minecraft:stone");
        form.caption(Component.translatable("makeium.display.block"));
        MkField id = form.field(Component.translatable("makeium.display.block_id"), name, s -> {
            Identifier rid = Identifier.tryParse(s);
            if (rid != null && BuiltInRegistries.BLOCK.containsKey(rid)) {
                setBlock(rid);
            }
        });
        form.button(Component.translatable("makeium.display.pick_block"), MkButton.Kind.DEFAULT,
                () -> openPopup(new MkPicker(this, Component.translatable("makeium.display.pick_block"), MkPicker.blocks(), rid -> {
                    setBlock(rid);
                    rebuild();
                })));
        Identifier rid = Identifier.tryParse(name);
        Block block = rid == null ? null : BuiltInRegistries.BLOCK.getOptional(rid).orElse(null);
        if (block == null) return;
        BlockState state = readState(block, stateTag);
        form.caption(Component.translatable("makeium.display.block_state"));
        for (Property<?> p : block.getStateDefinition().getProperties()) {
            addPropertyDropdown(block, p, state);
        }
        id.setInvalid(false);
    }

    private <T extends Comparable<T>> void addPropertyDropdown(Block block, Property<T> p, BlockState state) {
        List<T> values = new ArrayList<>(p.getPossibleValues());
        form.dropdown(Component.literal(p.getName()), values, state.getValue(p), v -> Component.literal(p.getName(v)), v -> {
            BlockState s = readState(block, data.tag.getCompoundOrEmpty("block_state")).setValue(p, v);
            data.tag.put("block_state", NbtUtils.writeBlockState(s));
            dirty = true;
        });
    }

    private BlockState readState(Block block, CompoundTag tag) {
        BlockState s = block.defaultBlockState();
        CompoundTag props = tag.getCompoundOrEmpty("Properties");
        for (Property<?> p : block.getStateDefinition().getProperties()) {
            s = withProp(s, p, props.getStringOr(p.getName(), null));
        }
        return s;
    }

    private static <T extends Comparable<T>> BlockState withProp(BlockState s, Property<T> p, String value) {
        if (value == null) return s;
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }

    private void setBlock(Identifier rid) {
        Block b = BuiltInRegistries.BLOCK.getValue(rid);
        data.tag.put("block_state", NbtUtils.writeBlockState(b.defaultBlockState()));
        dirty = true;
    }

    private void buildItem() {
        CompoundTag item = data.tag.getCompoundOrEmpty("item");
        form.caption(Component.translatable("makeium.display.item"));
        form.field(Component.translatable("makeium.display.item_id"), item.getStringOr("id", "minecraft:diamond"), s -> {
            Identifier rid = Identifier.tryParse(s);
            if (rid != null && BuiltInRegistries.ITEM.containsKey(rid)) setItem(rid);
        });
        form.button(Component.translatable("makeium.display.pick_item"), MkButton.Kind.DEFAULT,
                () -> openPopup(new MkPicker(this, Component.translatable("makeium.display.pick_item"), MkPicker.items(), rid -> {
                    setItem(rid);
                    rebuild();
                })));
        CompoundTag comps = item.getCompoundOrEmpty("components");
        form.field(Component.translatable("makeium.display.item_model"), comps.getStringOr("minecraft:item_model", ""), s -> {
            CompoundTag it = data.tag.getCompoundOrEmpty("item").copy();
            CompoundTag c = it.getCompoundOrEmpty("components").copy();
            if (s.isBlank() || Identifier.tryParse(s) == null) c.remove("minecraft:item_model");
            else c.putString("minecraft:item_model", s);
            if (c.isEmpty()) it.remove("components");
            else it.put("components", c);
            data.tag.put("item", it);
            dirty = true;
        });
        String mode = data.getString("item_display", "none");
        form.dropdown(Component.translatable("makeium.display.item_mode"), List.of(ITEM_MODES), mode, Component::literal, v -> {
            data.tag.putString("item_display", v);
            dirty = true;
        });
    }

    private void setItem(Identifier rid) {
        CompoundTag it = data.tag.getCompoundOrEmpty("item").copy();
        it.putString("id", rid.toString());
        if (!it.contains("count")) it.putInt("count", 1);
        data.tag.put("item", it);
        dirty = true;
    }

    // ---------------------------------------------------------------- look

    private void buildLook() {
        if (data.kind == WallSnap.Kind.TEXT) {
            form.caption(Component.translatable("makeium.display.text_look"));
            form.slider(Component.translatable("makeium.display.line_width"), 10, 1000, 10, data.getInt("line_width", 200), v -> {
                data.tag.putInt("line_width", (int) v);
                if (autoCenter) recenter();
                dirty = true;
            });
            form.dropdown(Component.translatable("makeium.display.align"), List.of(ALIGNS), data.getString("alignment", "center"),
                    v -> Component.translatable("makeium.display.align." + v), v -> {
                        data.tag.putString("alignment", v);
                        dirty = true;
                    });
            form.color(Component.translatable("makeium.display.background"), data.getInt("background", 0x40000000), true, c -> {
                data.tag.putInt("background", c);
                dirty = true;
            });
            form.slider(Component.translatable("makeium.display.opacity"), 26, 255, 1, data.textOpacity(), v -> {
                data.setTextOpacity((int) v);
                dirty = true;
            });
            form.toggle(Component.translatable("makeium.display.shadow"), data.getBool("shadow", false), v -> {
                data.tag.putBoolean("shadow", v);
                dirty = true;
            });
            form.toggle(Component.translatable("makeium.display.see_through"), data.getBool("see_through", false), v -> {
                data.tag.putBoolean("see_through", v);
                dirty = true;
            });
            form.toggle(Component.translatable("makeium.display.default_background"), data.getBool("default_background", false), v -> {
                data.tag.putBoolean("default_background", v);
                dirty = true;
            });
        }
        form.caption(Component.translatable("makeium.display.render"));
        form.dropdown(Component.translatable("makeium.display.billboard"), List.of(BILLBOARDS), data.getString("billboard", "fixed"),
                v -> Component.translatable("makeium.display.billboard." + v), v -> {
                    data.tag.putString("billboard", v);
                    dirty = true;
                });
        form.slider(Component.translatable("makeium.display.brightness"), -1, 15, 1, data.brightness(), v -> {
            data.setBrightness((int) v);
            dirty = true;
        }).format(v -> v < 0 ? Component.translatable("makeium.display.brightness.auto").getString() : Integer.toString((int) v));
        form.slider(Component.translatable("makeium.display.view_range"), 0.1, 10, 0.1, data.getFloat("view_range", 1f), v -> {
            data.tag.putFloat("view_range", (float) v);
            dirty = true;
        });
        if (data.kind != WallSnap.Kind.TEXT) {
            form.toggle(Component.translatable("makeium.display.glowing"), data.getBool("Glowing", false), v -> {
                data.tag.putBoolean("Glowing", v);
                dirty = true;
            });
            form.color(Component.translatable("makeium.display.glow_color"), data.getInt("glow_color_override", -1) == -1 ? 0xFFFFFFFF : data.getInt("glow_color_override", -1), false, c -> {
                data.tag.putInt("glow_color_override", c & 0xFFFFFF);
                dirty = true;
            });
        }
        form.slider(Component.translatable("makeium.display.shadow_radius"), 0, 4, 0.05, data.getFloat("shadow_radius", 0f), v -> {
            data.tag.putFloat("shadow_radius", (float) v);
            dirty = true;
        });
    }

    // ---------------------------------------------------------------- transform

    private void buildTransform() {
        form.caption(Component.translatable("makeium.display.transformation"));
        form.vec3(Component.translatable("makeium.display.translation"), data.translation(), v -> {
            data.setTranslation(v);
            autoCenter = false;
            dirty = true;
        });
        form.vec3(Component.translatable("makeium.display.rotation"), data.leftEuler(), v -> {
            data.setLeftEuler(v);
            dirty = true;
        });
        form.vec3(Component.translatable("makeium.display.scale"), data.scale(), v -> {
            data.setScale(v);
            if (autoCenter) recenter();
            dirty = true;
        });
        form.vec3(Component.translatable("makeium.display.right_rotation"), data.rightEuler(), v -> {
            data.setRightEuler(v);
            dirty = true;
        });
        form.button(Component.translatable("makeium.display.uniform_scale"), MkButton.Kind.DEFAULT, () -> {
            float s = data.scale().x;
            data.setScale(new Vector3f(s));
            if (autoCenter) recenter();
            dirty = true;
            rebuild();
        });
        form.caption(Component.translatable("makeium.display.position"));
        Component[] nudges = {Component.literal("X−"), Component.literal("X+"), Component.literal("Y−"), Component.literal("Y+"), Component.literal("Z−"), Component.literal("Z+")};
        Runnable[] acts = new Runnable[6];
        for (int i = 0; i < 6; i++) {
            final int axis = i / 2;
            final double step = (i % 2 == 0 ? -1 : 1) / 16.0;
            acts[i] = () -> nudge(axis, step);
        }
        form.buttons(MkButton.Kind.DEFAULT, nudges, acts);
        form.label(Component.translatable("makeium.display.nudge_hint"));
        Entity e = entity();
        if (e != null) {
            form.numberInline(Component.translatable("makeium.display.yaw"), e.getYRot(), 80, v -> rotate((float) v, null));
            form.numberInline(Component.translatable("makeium.display.pitch"), e.getXRot(), 80, v -> rotate(null, (float) v));
        }
    }

    private Entity entity() {
        if (minecraft == null || minecraft.level == null) return null;
        for (Entity e : minecraft.level.entitiesForRendering()) {
            if (e.getUUID().toString().equals(uuid)) return e;
        }
        return null;
    }

    private void nudge(int axis, double step) {
        Entity e = entity();
        if (e == null) return;
        CompoundTag d = new CompoundTag();
        d.putString("uuid", uuid);
        d.putDouble("x", e.getX() + (axis == 0 ? step : 0));
        d.putDouble("y", e.getY() + (axis == 1 ? step : 0));
        d.putDouble("z", e.getZ() + (axis == 2 ? step : 0));
        d.putFloat("yaw", e.getYRot());
        d.putFloat("pitch", e.getXRot());
        Net.sendToServer("display/update", d);
    }

    private void rotate(Float yaw, Float pitch) {
        Entity e = entity();
        if (e == null) return;
        CompoundTag d = new CompoundTag();
        d.putString("uuid", uuid);
        d.putFloat("yaw", yaw != null ? yaw : e.getYRot());
        d.putFloat("pitch", pitch != null ? pitch : e.getXRot());
        Net.sendToServer("display/update", d);
    }

    // ---------------------------------------------------------------- animation

    private void buildAnimation() {
        form.caption(Component.translatable("makeium.display.interpolation"));
        form.numberInline(Component.translatable("makeium.display.interp_duration"), data.getInt("interpolation_duration", 0), 70, v -> {
            data.tag.putInt("interpolation_duration", (int) v);
            dirty = true;
        }).integer();
        form.numberInline(Component.translatable("makeium.display.teleport_duration"), data.getInt("teleport_duration", 0), 70, v -> {
            data.tag.putInt("teleport_duration", (int) Math.max(0, Math.min(59, v)));
            dirty = true;
        }).integer();
        form.caption(Component.translatable("makeium.display.keyframes"));
        MkList list = form.add(new MkList(form.x, form.y, form.w, 70));
        List<MkList.Item> rows = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            final int idx = i;
            Key k = keys.get(i);
            rows.add(new MkList.Row(Component.translatable("makeium.display.keyframe", i + 1),
                    Component.literal(String.format(Locale.ROOT, "T %.2f %.2f %.2f · S %.2f", k.t.x, k.t.y, k.t.z, k.s.x)),
                    Component.literal(k.ticks + "t"), () -> {
                        applyKey(keys.get(idx), 0);
                        dirty = true;
                    }));
        }
        if (rows.isEmpty()) rows.add(new MkList.Note(Component.translatable("makeium.display.no_keyframes"), MkTheme.FAINT));
        list.setItems(rows);
        form.y += 74;
        MkNumberField ticks = form.numberInline(Component.translatable("makeium.display.key_ticks"), 20, 70, v -> {});
        ticks.integer();
        form.buttons(MkButton.Kind.DEFAULT,
                new Component[]{Component.translatable("makeium.display.add_key"), Component.translatable("makeium.display.remove_key")},
                new Runnable[]{() -> {
                    Key k = new Key();
                    k.t = data.translation();
                    k.l = data.leftEuler();
                    k.s = data.scale();
                    k.r = data.rightEuler();
                    k.ticks = (int) Math.max(1, ticks.number(20));
                    keys.add(k);
                    rebuild();
                }, () -> {
                    if (!keys.isEmpty()) keys.remove(keys.size() - 1);
                    rebuild();
                }});
        form.toggle(Component.translatable("makeium.display.loop"), animLoop, v -> animLoop = v);
        form.field(Component.translatable("makeium.display.anim_name"), animName, s -> animName = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
        form.buttons(MkButton.Kind.PRIMARY,
                new Component[]{Component.translatable("makeium.display.preview"), Component.translatable("makeium.display.export")},
                new Runnable[]{this::playPreview, this::exportAnimation});
    }

    private void applyKey(Key k, int interpolation) {
        data.setTransformation(k.t, k.l, k.s, k.r);
        data.tag.putInt("interpolation_duration", interpolation);
        data.tag.putInt("start_interpolation", 0);
    }

    private void playPreview() {
        if (keys.size() < 2) {
            MkToast.error(Component.translatable("makeium.display.need_two_keys"));
            return;
        }
        applyKey(keys.get(0), 0);
        sendNow();
        playIndex = 1;
        playWait = 2;
    }

    private void tickPreview() {
        if (playIndex < 0) return;
        if (--playWait > 0) return;
        if (playIndex >= keys.size()) {
            if (animLoop) {
                playIndex = 0;
            } else {
                playIndex = -1;
                return;
            }
        }
        Key prev = keys.get(playIndex == 0 ? keys.size() - 1 : playIndex - 1);
        applyKey(keys.get(playIndex), prev.ticks);
        sendNow();
        playWait = prev.ticks;
        playIndex++;
    }

    /** Writes the keyframes as a vanilla function chain into the "makeium_anim" datapack and tags the entity. */
    private void exportAnimation() {
        if (keys.size() < 2) {
            MkToast.error(Component.translatable("makeium.display.need_two_keys"));
            return;
        }
        String name = animName.isBlank() ? "anim" : animName;
        String tagName = "mk_anim_" + name;
        // tag the entity so the functions can find it
        ListTag tags = data.tag.getListOrEmpty("Tags").copy();
        if (!tags.contains(StringTag.valueOf(tagName))) tags.add(StringTag.valueOf(tagName));
        data.tag.put("Tags", tags);
        dirty = true;
        String type = switch (data.kind) {
            case TEXT -> "text_display";
            case BLOCK -> "block_display";
            case ITEM -> "item_display";
        };
        String sel = "@e[type=minecraft:" + type + ",tag=" + tagName + "]";
        CompoundTag files = new CompoundTag();
        String base = "data/makeium_anim/function/" + name + "/";
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            Key prev = keys.get(i == 0 ? keys.size() - 1 : i - 1);
            int interp = i == 0 ? 0 : prev.ticks;
            StringBuilder sb = new StringBuilder();
            sb.append("# Makeium animation '").append(name).append("' frame ").append(i + 1).append('\n');
            sb.append("data merge entity ").append(sel.replace("]", ",limit=1]")).append(" {start_interpolation:0,interpolation_duration:")
                    .append(interp).append(",transformation:").append(transformSnbt(k)).append("}\n");
            if (i + 1 < keys.size()) {
                sb.append("schedule function makeium_anim:").append(name).append("/f").append(i + 1).append(' ').append(k.ticks).append("t replace\n");
            } else if (animLoop) {
                sb.append("schedule function makeium_anim:").append(name).append("/f0 ").append(k.ticks).append("t replace\n");
            }
            files.putString(base + "f" + i + ".mcfunction", sb.toString());
        }
        files.putString(base + "play.mcfunction", "# Plays '" + name + "' on every display tagged " + tagName + "\nfunction makeium_anim:" + name + "/f0\n");
        files.putString(base + "stop.mcfunction", "schedule clear makeium_anim:" + name + "/f0\n" + stopLines(name));
        CompoundTag msg = new CompoundTag();
        msg.putString("name", "makeium_anim");
        msg.putString("description", "Makeium display animations");
        msg.put("files", files);
        Net.sendToServer("dp/save", msg);
        MkToast.ok(Component.translatable("makeium.display.exported", "/function makeium_anim:" + name + "/play"));
    }

    private String stopLines(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < keys.size(); i++) sb.append("schedule clear makeium_anim:").append(name).append("/f").append(i).append('\n');
        return sb.toString();
    }

    private static String transformSnbt(Key k) {
        var l = DisplayData.quat(k.l);
        var r = DisplayData.quat(k.r);
        return String.format(Locale.ROOT, "{translation:[%sf,%sf,%sf],left_rotation:[%sf,%sf,%sf,%sf],scale:[%sf,%sf,%sf],right_rotation:[%sf,%sf,%sf,%sf]}",
                f(k.t.x), f(k.t.y), f(k.t.z), f(l.x), f(l.y), f(l.z), f(l.w), f(k.s.x), f(k.s.y), f(k.s.z), f(r.x), f(r.y), f(r.z), f(r.w));
    }

    private static String f(float v) {
        return MkNumberField.format(v, false);
    }

    // ---------------------------------------------------------------- syncing

    @Override
    public void tick() {
        super.tick();
        tickPreview();
        sinceSend++;
        if (dirty && sinceSend >= 3) sendNow();
    }

    private void sendNow() {
        dirty = false;
        sinceSend = 0;
        CompoundTag d = new CompoundTag();
        d.putString("uuid", uuid);
        d.put("nbt", data.tag.copy());
        Net.sendToServer("display/update", d);
    }

    @Override
    public void removed() {
        if (dirty) sendNow();
        super.removed();
    }

    private void copySummon() {
        String type = switch (data.kind) {
            case TEXT -> "text_display";
            case BLOCK -> "block_display";
            case ITEM -> "item_display";
        };
        Entity e = entity();
        String pos = e == null ? "~ ~ ~" : String.format(Locale.ROOT, "%.4f %.4f %.4f", e.getX(), e.getY(), e.getZ());
        CompoundTag t = data.tag.copy();
        if (e != null) t.put("Rotation", DisplayData.floats(e.getYRot(), e.getXRot()));
        String cmd = "summon minecraft:" + type + " " + pos + " " + NbtUtils.structureToSnbt(t).replaceAll("\\s*\\n\\s*", "");
        minecraft.keyboardHandler.setClipboard("/" + cmd);
        MkToast.ok(Component.translatable("makeium.display.copied"));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void drawChrome(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.drawChrome(g, mouseX, mouseY);
        int pw = panelW();
        g.fill(0, TOP, pw, height, MkTheme.withAlpha(MkTheme.PANEL, 0.96f));
        MkTheme.vline(g, pw, TOP, height, MkTheme.LINE);
        MkTheme.hline(g, 0, pw, TOP + 22, MkTheme.LINE);
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        clipToScrollArea(g, () -> {
            if (form != null) form.draw(g);
            if (textBox != null) {
                int x = textBox.getX() - 1, y = textBox.getY() - 1;
                g.fill(x, y, x + textBox.getWidth() + 2, y + textBox.getHeight() + 2, MkTheme.INPUT);
                MkTheme.border(g, x, y, textBox.getWidth() + 2, textBox.getHeight() + 2, textBox.isFocused() ? MkTheme.GREEN_HOVER : MkTheme.LINE);
            }
        });
        if (data.kind == WallSnap.Kind.TEXT) drawTextPreview(g);
    }

    /** A 1:1 (GUI pixel = text pixel) preview of the text with the real font, alignment and background. */
    private void drawTextPreview(GuiGraphicsExtractor g) {
        Component text = data.text(regs());
        int lineWidth = data.getInt("line_width", 200);
        var font = minecraft.font;
        List<FormattedCharSequence> lines = font.split(text, Math.max(1, lineWidth));
        int tw = 0;
        for (FormattedCharSequence l : lines) tw = Math.max(tw, font.width(l));
        int th = Math.max(1, lines.size()) * 10 - 1;
        int boxW = Math.max(160, tw + 24), boxH = th + 40;
        int x = width - PAD - boxW, y = height - PAD - boxH;
        MkTheme.card(g, x, y, boxW, boxH);
        MkTheme.caption(g, Component.translatable("makeium.display.preview_title"), x + 8, y + 6);
        int ox = x + (boxW - tw) / 2, oy = y + 24;
        int bg = data.getBool("default_background", false) ? 0x40000000 : data.getInt("background", 0x40000000);
        if (bg != 0) g.fill(ox - 1, oy - 1, ox + tw + 1, oy + th + 1, bg);
        String align = data.getString("alignment", "center");
        int alpha = data.textOpacity();
        int yy = oy;
        for (FormattedCharSequence l : lines) {
            int lw = font.width(l);
            int lx = switch (align) {
                case "left" -> ox;
                case "right" -> ox + tw - lw;
                default -> ox + (tw - lw) / 2;
            };
            g.text(font, l, lx, yy, (alpha << 24) | 0xFFFFFF, data.getBool("shadow", false));
            yy += 10;
        }
    }
}
