package com.hyproj.makeium.client.datapack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkNumberField;
import com.hyproj.makeium.client.ui.MkPicker;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToggle;
import com.hyproj.makeium.client.ui.MkWidgetPopup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A crafting-table grid for shaped/shapeless recipes: click a cell to pick an item, right-click to clear. */
public final class RecipeGridPopup extends MkWidgetPopup {
    private static final int CELL = 24;
    private final MkScreen screen;
    private final String[] grid = new String[9];
    private String result = "minecraft:diamond";
    private final MkNumberField count;
    private final MkToggle shapeless;
    private final MkButton apply;
    private final Consumer<String> done;

    public RecipeGridPopup(MkScreen screen, String json, Consumer<String> done) {
        super((screen.width - 260) / 2, (screen.height - 170) / 2, 260, 170);
        this.screen = screen;
        this.done = done;
        boolean isShapeless = false;
        int resultCount = 1;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String type = o.has("type") ? o.get("type").getAsString() : "";
            isShapeless = type.endsWith("crafting_shapeless");
            if (o.has("result")) {
                JsonObject r = o.getAsJsonObject("result");
                if (r.has("id")) result = r.get("id").getAsString();
                if (r.has("count")) resultCount = r.get("count").getAsInt();
            }
            if (isShapeless && o.has("ingredients")) {
                int i = 0;
                for (JsonElement e : o.getAsJsonArray("ingredients")) if (i < 9) grid[i++] = ingredient(e);
            } else if (o.has("pattern") && o.has("key")) {
                JsonObject key = o.getAsJsonObject("key");
                JsonArray pattern = o.getAsJsonArray("pattern");
                for (int r = 0; r < Math.min(3, pattern.size()); r++) {
                    String row = pattern.get(r).getAsString();
                    for (int c = 0; c < Math.min(3, row.length()); c++) {
                        String k = String.valueOf(row.charAt(c));
                        if (key.has(k)) grid[r * 3 + c] = ingredient(key.get(k));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // start empty
        }
        count = child(new MkNumberField(0, 0, 50, 18, resultCount));
        count.integer().range(1, 99);
        shapeless = child(new MkToggle(0, 0, 120, Component.translatable("makeium.dp.shapeless"), isShapeless, v -> {}));
        apply = child(new MkButton(0, 0, 90, 20, Component.translatable("makeium.ui.apply"), MkButton.Kind.PRIMARY, this::write));
    }

    private static String ingredient(JsonElement e) {
        if (e.isJsonPrimitive()) return e.getAsString();
        if (e.isJsonArray() && !e.getAsJsonArray().isEmpty()) return ingredient(e.getAsJsonArray().get(0));
        if (e.isJsonObject() && e.getAsJsonObject().has("item")) return e.getAsJsonObject().get("item").getAsString();
        return null;
    }

    @Override
    protected void layout() {
        count.moveBox(x + 196, y + 92);
        shapeless.setPosition(x + 12, y + h - 50);
        apply.setPosition(x + w - 102, y + h - 28);
    }

    private ItemStack icon(String id) {
        if (id == null) return ItemStack.EMPTY;
        Identifier rid = Identifier.tryParse(id.startsWith("#") ? id.substring(1) : id);
        if (rid == null || !BuiltInRegistries.ITEM.containsKey(rid)) return new ItemStack(Items.BARRIER);
        return new ItemStack(BuiltInRegistries.ITEM.getValue(rid));
    }

    @Override
    protected void renderFrame(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        MkTheme.modal(g, x, y, w, h);
        MkTheme.boldText(g, Component.translatable("makeium.dp.recipe_grid"), x + 12, y + 10, MkTheme.TEXT);
        int gx = x + 12, gy = y + 30;
        for (int i = 0; i < 9; i++) {
            int cx = gx + (i % 3) * CELL, cy = gy + (i / 3) * CELL;
            boolean hot = mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2;
            g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, hot ? MkTheme.SURFACE_HOVER : MkTheme.SURFACE);
            ItemStack st = icon(grid[i]);
            if (!st.isEmpty()) g.item(st, cx + 3, cy + 3);
        }
        // arrow
        g.fill(gx + 3 * CELL + 10, gy + CELL + 9, gx + 3 * CELL + 40, gy + CELL + 12, MkTheme.MUTED);
        int rx = x + 196, ry = gy + CELL - 2;
        boolean hot = mouseX >= rx && mouseX < rx + 28 && mouseY >= ry && mouseY < ry + 28;
        g.fill(rx, ry, rx + 28, ry + 28, hot ? MkTheme.SURFACE_HOVER : MkTheme.SURFACE);
        MkTheme.border(g, rx, ry, 28, 28, MkTheme.GREEN);
        g.item(icon(result), rx + 6, ry + 6);
        MkTheme.text(g, Component.translatable("makeium.dp.grid_hint"), x + 12, y + h - 30, MkTheme.FAINT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        int gx = x + 12, gy = y + 30;
        boolean right = e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
        for (int i = 0; i < 9; i++) {
            int cx = gx + (i % 3) * CELL, cy = gy + (i / 3) * CELL;
            if (e.x() >= cx && e.x() < cx + CELL - 2 && e.y() >= cy && e.y() < cy + CELL - 2) {
                final int idx = i;
                if (right) grid[i] = null;
                else screen.openPopup(new MkPicker(screen, Component.translatable("makeium.display.pick_item"), MkPicker.items(), id -> grid[idx] = id.toString()));
                return true;
            }
        }
        int rx = x + 196, ry = gy + CELL - 2;
        if (e.x() >= rx && e.x() < rx + 28 && e.y() >= ry && e.y() < ry + 28) {
            screen.openPopup(new MkPicker(screen, Component.translatable("makeium.display.pick_item"), MkPicker.items(), id -> result = id.toString()));
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean closeOnOutsideClick() {
        return false;
    }

    @Override
    public boolean dims() {
        return true;
    }

    private void write() {
        JsonObject o = new JsonObject();
        JsonObject res = new JsonObject();
        res.addProperty("id", result);
        res.addProperty("count", (int) count.number(1));
        if (shapeless.value()) {
            o.addProperty("type", "minecraft:crafting_shapeless");
            o.addProperty("category", "misc");
            JsonArray ing = new JsonArray();
            for (String s : grid) if (s != null) ing.add(s);
            o.add("ingredients", ing);
        } else {
            o.addProperty("type", "minecraft:crafting_shaped");
            o.addProperty("category", "misc");
            Map<String, Character> keys = new LinkedHashMap<>();
            String symbols = "#XABCDEFG";
            // trim empty rows/columns so small recipes work anywhere in the grid
            int minR = 3, maxR = -1, minC = 3, maxC = -1;
            for (int i = 0; i < 9; i++) {
                if (grid[i] == null) continue;
                minR = Math.min(minR, i / 3);
                maxR = Math.max(maxR, i / 3);
                minC = Math.min(minC, i % 3);
                maxC = Math.max(maxC, i % 3);
            }
            JsonArray pattern = new JsonArray();
            for (int r = minR; r <= maxR; r++) {
                StringBuilder row = new StringBuilder();
                for (int c = minC; c <= maxC; c++) {
                    String id = grid[r * 3 + c];
                    if (id == null) row.append(' ');
                    else row.append(keys.computeIfAbsent(id, k -> symbols.charAt(Math.min(keys.size(), symbols.length() - 1))));
                }
                pattern.add(row.toString());
            }
            o.add("pattern", pattern);
            JsonObject key = new JsonObject();
            keys.forEach((id, ch) -> key.addProperty(String.valueOf(ch), id));
            o.add("key", key);
        }
        o.add("result", res);
        close();
        done.accept(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) + "\n");
    }
}
