package com.hyproj.makeium.client.resourcepack;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.hyproj.makeium.Makeium;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

/**
 * Thumbnails of resource-pack textures for the texture browser: each PNG is read once through the resource manager
 * (so it shows the pack's override when there is one) and kept as a dynamic texture; at most {@link #MAX} stay loaded
 * and only a few are read per frame so scrolling stays smooth.
 */
public final class TextureCache {
    private static final int MAX = 400;
    private static final int LOADS_PER_FRAME = 12;

    public record Thumb(Identifier texture, int width, int height) {
        /** Height of one animation frame (square frames when the texture is a vertical strip). */
        public int frameHeight() {
            return height > width && height % width == 0 ? width : height;
        }
    }

    private static final Map<Identifier, Optional<Thumb>> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static int loadsThisFrame;
    private static int counter;

    private TextureCache() {}

    public static void newFrame() {
        loadsThisFrame = 0;
    }

    /** The thumbnail of {@code png} (e.g. minecraft:textures/block/stone.png), or null while not loaded yet / missing. */
    public static @Nullable Thumb get(Identifier png) {
        Optional<Thumb> t = CACHE.get(png);
        if (t != null) return t.orElse(null);
        if (loadsThisFrame >= LOADS_PER_FRAME) return null;
        loadsThisFrame++;
        Thumb thumb = load(png);
        CACHE.put(png, Optional.ofNullable(thumb));
        trim();
        return thumb;
    }

    private static @Nullable Thumb load(Identifier png) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Resource> res = mc.getResourceManager().getResource(png);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) {
            NativeImage img = NativeImage.read(in);
            Identifier id = Makeium.id("thumb/" + (counter++));
            mc.getTextureManager().register(id, new DynamicTexture(() -> "makeium thumbnail " + png, img));
            return new Thumb(id, img.getWidth(), img.getHeight());
        } catch (Exception e) {
            return null;
        }
    }

    private static void trim() {
        while (CACHE.size() > MAX) {
            Identifier oldest = CACHE.keySet().iterator().next();
            Optional<Thumb> t = CACHE.remove(oldest);
            t.ifPresent(th -> Minecraft.getInstance().getTextureManager().release(th.texture()));
        }
    }

    /** Forget everything (after a resource reload the pixels may have changed). */
    public static void clear() {
        List<Optional<Thumb>> all = new ArrayList<>(CACHE.values());
        CACHE.clear();
        for (Optional<Thumb> t : all) t.ifPresent(th -> Minecraft.getInstance().getTextureManager().release(th.texture()));
    }

    public static void invalidate(Identifier png) {
        Optional<Thumb> t = CACHE.remove(png);
        if (t != null) t.ifPresent(th -> Minecraft.getInstance().getTextureManager().release(th.texture()));
    }

    /** Draws the first frame of {@code t} into a size×size square. */
    public static void draw(GuiGraphicsExtractor g, Thumb t, int x, int y, int size) {
        int fh = t.frameHeight();
        g.blit(RenderPipelines.GUI_TEXTURED, t.texture(), x, y, 0, 0, size, size, t.width(), fh, t.width(), t.height());
    }
}
