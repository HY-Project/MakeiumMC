package com.hyproj.makeium.client.datapack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkDropdown;
import com.hyproj.makeium.client.ui.MkField;
import com.hyproj.makeium.client.ui.MkPicker;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkWidgetPopup;
import com.hyproj.makeium.feature.datapack.SlotDef;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Editing one block input. Lists (choices, registry ids) open a filterable list; everything else opens a small editor
 * with a text field plus helpers for the slot's type: positions (mine / looked-at block), selectors, block and item
 * pickers, quick rich text, condition templates, and live command validation with suggestions from the server's
 * command tree (the same one chat uses).
 */
public final class SlotPopup extends MkWidgetPopup {
    private final SlotDef slot;
    private final MkField field;
    private final Consumer<String> commit;
    private final List<MkButton> helpers = new ArrayList<>();
    private @Nullable String error;
    private List<String> suggestions = List.of();
    private CompletableFuture<Suggestions> pending;
    private final String commandPrefix;

    private SlotPopup(MkScreen screen, int x, int y, SlotDef slot, String value, String commandPrefix, Consumer<String> commit) {
        super(x, y, Math.max(240, Math.min(screen.width - 20, 360)), 80);
        this.slot = slot;
        this.commit = commit;
        this.commandPrefix = commandPrefix;
        field = child(slot.type() == SlotDef.Type.COMMAND || slot.type() == SlotDef.Type.NBT || slot.type() == SlotDef.Type.RICH_TEXT
                || slot.type() == SlotDef.Type.CONDITION || slot.type() == SlotDef.Type.SELECTOR
                ? MkField.mono(0, 0, w - 16, 18, Component.empty()) : new MkField(0, 0, w - 16, 18, Component.empty()));
        field.value(value);
        field.moveCursorToEnd(false);
        field.onChange(s -> validate());
        focus(field);
        addHelpers(screen);
        h = 58 + (helpers.isEmpty() ? 0 : 24) + (isCommandLike() ? 52 : 0);
        validate();
    }

    /** Opens the right editor for {@code slot} near (x, y). {@code commandPrefix} is prepended for validation. */
    public static void open(MkScreen screen, int x, int y, SlotDef slot, String value, String commandPrefix, Consumer<String> commit) {
        switch (slot.type()) {
            case CHOICE -> screen.openPopup(new MkDropdown.ListPopup<>(x, y, 160, slot.choices(), value, Component::literal, commit));
            case REGISTRY -> {
                List<String> ids = registryIds(slot.registry());
                if (ids.isEmpty()) {
                    screen.openPopup(new SlotPopup(screen, x, y, SlotDef.of(slot.name(), SlotDef.Type.TEXT, slot.def()), value, "", commit));
                } else {
                    screen.openPopup(new MkDropdown.ListPopup<>(x, y, 240, ids, value, Component::literal, commit));
                }
            }
            default -> screen.openPopup(new SlotPopup(screen, x, y, slot, value, commandPrefix, commit));
        }
    }

    /** Ids of a built-in registry, or of a data-driven one the client knows (enchantment, dialog, ...). */
    public static List<String> registryIds(String registry) {
        Identifier rid = Identifier.withDefaultNamespace(registry);
        List<String> out = new ArrayList<>();
        Registry<?> r = BuiltInRegistries.REGISTRY.getValue(rid);
        if (r != null) {
            for (Identifier id : r.keySet()) out.add(id.toString());
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                mc.level.registryAccess().lookup(ResourceKey.createRegistryKey(rid)).ifPresent(reg -> {
                    for (Identifier id : reg.keySet()) out.add(id.toString());
                });
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    private boolean isCommandLike() {
        return slot.type() == SlotDef.Type.COMMAND || slot.type() == SlotDef.Type.CONDITION || slot.type() == SlotDef.Type.SELECTOR
                || slot.type() == SlotDef.Type.POS || slot.type() == SlotDef.Type.BLOCK || slot.type() == SlotDef.Type.ITEM;
    }

    private void helper(String label, Runnable r) {
        helpers.add(child(new MkButton(0, 0, Math.max(28, MkTheme.font().width(label) + 12), 18, Component.literal(label), MkButton.Kind.CHIP, r)));
    }

    private void insert(String s) {
        field.setValue(s);
        field.moveCursorToEnd(false);
        validate();
    }

    private void addHelpers(MkScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        switch (slot.type()) {
            case POS -> {
                helper("~ ~ ~", () -> insert("~ ~ ~"));
                helper(Component.translatable("makeium.slot.my_pos").getString(), () -> {
                    if (mc.player != null) {
                        BlockPos p = mc.player.blockPosition();
                        insert(p.getX() + " " + p.getY() + " " + p.getZ());
                    }
                });
                helper(Component.translatable("makeium.slot.looked_block").getString(), () -> {
                    if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
                        BlockPos p = bh.getBlockPos();
                        insert(p.getX() + " " + p.getY() + " " + p.getZ());
                    }
                });
            }
            case SELECTOR -> {
                for (String s : new String[]{"@a", "@p", "@s", "@e", "@r", "@n"}) helper(s, () -> insert(s));
                helper("[tag=]", () -> insert(appendArg(field.getValue(), "tag=")));
                helper("[distance=..5]", () -> insert(appendArg(field.getValue(), "distance=..5")));
                helper("[limit=1]", () -> insert(appendArg(field.getValue(), "limit=1")));
            }
            case BLOCK -> {
                helper(Component.translatable("makeium.slot.pick").getString(), () -> screen.openPopup(new MkPicker(screen, Component.translatable("makeium.display.pick_block"), MkPicker.blocks(), id -> {
                    commit.accept(id.toString());
                })));
                helper(Component.translatable("makeium.slot.looked_block").getString(), () -> {
                    if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && mc.level != null) {
                        insert(blockStateString(mc.level.getBlockState(bh.getBlockPos())));
                    }
                });
            }
            case ITEM -> {
                helper(Component.translatable("makeium.slot.pick").getString(), () -> screen.openPopup(new MkPicker(screen, Component.translatable("makeium.display.pick_item"), MkPicker.items(), id -> {
                    commit.accept(id.toString());
                })));
                helper(Component.translatable("makeium.slot.held_item").getString(), () -> {
                    if (mc.player != null && !mc.player.getMainHandItem().isEmpty()) insert(itemString(mc.player.getMainHandItem()));
                });
            }
            case CONDITION -> {
                helper("entity", () -> insert("entity @s[tag=vip]"));
                helper("block", () -> insert("block ~ ~-1 ~ minecraft:gold_block"));
                helper("score", () -> insert("score @s points matches 10.."));
                helper("predicate", () -> insert("predicate mymap:is_night"));
                helper("items", () -> insert("items entity @s weapon.mainhand minecraft:diamond_sword"));
                helper("dimension", () -> insert("dimension minecraft:the_nether"));
            }
            case RICH_TEXT -> {
                for (String c : new String[]{"white", "yellow", "gold", "green", "aqua", "red", "light_purple", "gray"}) {
                    helper(c, () -> insert("{text:\"" + plainText(field.getValue()).replace("\"", "\\\"") + "\",color:\"" + c + "\"}"));
                }
                helper("B", () -> insert(addFlag(field.getValue(), "bold")));
            }
            case FUNCTION -> {
                for (String fn : DatapackScreen.functionNames()) {
                    if (helpers.size() >= 8) break;
                    helper(fn, () -> insert(fn));
                }
            }
            default -> {
            }
        }
    }

    static String appendArg(String sel, String arg) {
        sel = sel.strip();
        if (sel.isEmpty()) sel = "@a";
        if (sel.endsWith("]")) return sel.substring(0, sel.length() - 1) + "," + arg + "]";
        return sel + "[" + arg + "]";
    }

    private static String plainText(String v) {
        v = v.strip();
        int i = v.indexOf("text:\"");
        if (i >= 0) {
            int end = v.indexOf('"', i + 6);
            if (end > i) return v.substring(i + 6, end);
        }
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) return v.substring(1, v.length() - 1);
        return v.startsWith("{") ? "" : v;
    }

    private static String addFlag(String v, String flag) {
        v = v.strip();
        if (!v.startsWith("{")) v = "{text:\"" + v.replace("\"", "\\\"") + "\"}";
        if (v.contains(flag + ":")) return v;
        return v.substring(0, v.length() - 1) + "," + flag + ":true}";
    }

    static String blockStateString(net.minecraft.world.level.block.state.BlockState s) {
        String str = s.toString(); // Block{minecraft:oak_stairs}[facing=east,...]
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        int br = str.indexOf('[');
        return br >= 0 ? id + str.substring(br) : id;
    }

    static String itemString(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    @Override
    protected void layout() {
        field.moveBox(x + 8, y + 22);
        int hx = x + 8, hy = y + 44;
        for (MkButton b : helpers) {
            if (hx + b.getWidth() > x + w - 8) {
                hx = x + 8;
                hy += 20;
            }
            b.setPosition(hx, hy);
            hx += b.getWidth() + 3;
        }
        int rows = helpers.isEmpty() ? 0 : (hy - (y + 44)) / 20 + 1;
        h = 50 + rows * 20 + (isCommandLike() ? 52 : 0) + 8;
    }

    // ---------------------------------------------------------------- validation (commands)

    private void validate() {
        error = null;
        suggestions = List.of();
        if (!isCommandLike()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        String text = commandText();
        if (text == null) return;
        try {
            var dispatcher = mc.getConnection().getCommands();
            ClientSuggestionProvider src = mc.getConnection().getSuggestionsProvider();
            ParseResults<ClientSuggestionProvider> parse = dispatcher.parse(text, src);
            CommandSyntaxException ex = Commands.getParseException(parse);
            if (ex != null) error = ex.getMessage();
            if (pending != null) pending.cancel(true);
            pending = dispatcher.getCompletionSuggestions(parse, text.length());
            pending.thenAccept(sug -> mc.execute(() -> {
                List<String> l = new ArrayList<>();
                for (Suggestion s : sug.getList()) {
                    l.add(s.getText());
                    if (l.size() >= 6) break;
                }
                suggestions = l;
            }));
        } catch (RuntimeException e) {
            error = e.getMessage();
        }
    }

    /** The full command to validate, or null when the slot is not checked as a command. */
    private @Nullable String commandText() {
        String v = field.getValue();
        return switch (slot.type()) {
            case COMMAND -> commandPrefix + (v.startsWith("/") ? v.substring(1) : v);
            case CONDITION -> "execute if " + v;
            case SELECTOR -> "tag " + v + " list";
            case POS -> "tp @s " + v;
            case BLOCK -> "setblock ~ ~ ~ " + v;
            case ITEM -> "give @s " + v;
            default -> null;
        };
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.isConfirmation()) {
            close();
            commit.accept(field.getValue());
            return true;
        }
        if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_TAB && !suggestions.isEmpty()) {
            applySuggestion(suggestions.get(0));
            return true;
        }
        return super.keyPressed(e);
    }

    private void applySuggestion(String s) {
        String v = field.getValue();
        int cut = v.lastIndexOf(' ') + 1;
        String prefix = v.substring(0, cut);
        insert(prefix + s);
    }

    @Override
    public void tick() {
        // keep suggestion futures flowing
    }

    @Override
    protected void renderFrame(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        MkTheme.modal(g, x, y, w, h);
        MkTheme.caption(g, Component.translatable("makeium.slot.type." + slot.type().name().toLowerCase(Locale.ROOT)), x + 8, y + 8);
        MkTheme.textRight(g, Component.translatable("makeium.slot.enter_hint").copy().withColor(MkTheme.FAINT), x + w - 8, y + 8, MkTheme.FAINT);
    }

    @Override
    protected void renderAfter(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (!isCommandLike()) return;
        int by = y + h - 56;
        if (error != null) {
            g.fill(x + 8, by, x + w - 8, by + 16, MkTheme.withAlpha(MkTheme.RED_DARK, 0.6f));
            MkTheme.text(g, MkTheme.clip(MkTheme.font(), Component.literal(error), w - 24), x + 12, by + 4, 0xFFFFD0D0);
        } else {
            g.fill(x + 8, by, x + w - 8, by + 16, MkTheme.withAlpha(MkTheme.GREEN_DARK, 0.6f));
            MkTheme.text(g, Component.translatable("makeium.slot.valid"), x + 12, by + 4, 0xFFD0FFD0);
        }
        int sx = x + 8;
        int sy = by + 20;
        for (String s : suggestions) {
            int sw = MkTheme.mono().width(s) + 8;
            if (sx + sw > x + w - 8) break;
            boolean hot = mouseX >= sx && mouseX < sx + sw && mouseY >= sy && mouseY < sy + 14;
            g.fill(sx, sy, sx + sw, sy + 14, hot ? MkTheme.SURFACE_HOVER : MkTheme.SURFACE);
            MkTheme.monoText(g, s, sx + 4, sy + 3, MkTheme.GREEN_TEXT);
            sx += sw + 3;
        }
        if (!suggestions.isEmpty()) MkTheme.text(g, Component.translatable("makeium.slot.tab_hint"), x + 8, sy + 18, MkTheme.FAINT);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) {
        if (isCommandLike() && !suggestions.isEmpty()) {
            int by = y + h - 56;
            int sx = x + 8, sy = by + 20;
            for (String s : suggestions) {
                int sw = MkTheme.mono().width(s) + 8;
                if (e.x() >= sx && e.x() < sx + sw && e.y() >= sy && e.y() < sy + 14) {
                    applySuggestion(s);
                    return true;
                }
                sx += sw + 3;
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public void close() {
        if (pending != null) pending.cancel(true);
        super.close();
    }

    @Override
    protected void onDismiss() {
        if (pending != null) pending.cancel(true);
        commit.accept(field.getValue());
    }
}
