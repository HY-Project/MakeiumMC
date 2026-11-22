package com.hyproj.makeium.server;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Makeium's tools are plain vanilla items tagged with {@code custom_data={makeium_tool:"<id>"}} — the mod registers no
 * items, so vanilla clients can still join a server running Makeium, and maps never depend on it.
 */
public enum Tools {
    TEXT_DISPLAY(Items.OAK_SIGN),
    BLOCK_DISPLAY(Items.GLASS),
    ITEM_DISPLAY(Items.ITEM_FRAME),
    DISPLAY_SELECT(Items.SPECTRAL_ARROW),
    SELECTION(Items.WOODEN_AXE),
    REGION(Items.EMERALD),
    LINK(Items.TRIPWIRE_HOOK),
    NPC(Items.NAME_TAG),
    CAMERA(Items.SPYGLASS);

    public static final String KEY = "makeium_tool";

    private final Item base;

    Tools(Item base) {
        this.base = base;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ItemStack create() {
        ItemStack stack = new ItemStack(base);
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("makeium.tool." + id()).withStyle(Style.EMPTY.withItalic(false).withColor(0x52A535)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("makeium.tool." + id() + ".hint").withStyle(Style.EMPTY.withItalic(false).withColor(0xA0A0A0)))));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /** The tool this stack is, or null. */
    public static Tools of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getStringOr(KEY, "");
        if (id.isEmpty()) return null;
        for (Tools t : values()) if (t.id().equals(id)) return t;
        return null;
    }

    public static Tools byId(String id) {
        for (Tools t : values()) if (t.id().equals(id)) return t;
        return null;
    }
}
