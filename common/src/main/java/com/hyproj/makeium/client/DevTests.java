package com.hyproj.makeium.client;

import java.util.List;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.client.feature.CommandBlockScreen;
import com.hyproj.makeium.client.feature.PackState;
import com.hyproj.makeium.client.resourcepack.PixelEditorScreen;
import com.hyproj.makeium.client.resourcepack.ResourcePackScreen;
import com.hyproj.makeium.client.resourcepack.RpProject;
import com.hyproj.makeium.client.tool.Selection;
import com.hyproj.makeium.feature.camera.CameraPack;
import com.hyproj.makeium.feature.link.LinkPack;
import com.hyproj.makeium.feature.npc.NpcPack;
import com.hyproj.makeium.feature.region.RegionPack;
import com.hyproj.makeium.net.Net;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.CommandBlockEntity;

/**
 * Dev runs only: drives Makeium features through the same code paths the screens use, for automated play-tests
 * ({@code test <name> [args]} in a dev script). Results are checked by the script with ordinary commands.
 */
final class DevTests {
    private DevTests() {}

    /** Runs one test step; returns ticks to wait afterwards. */
    static int run(String line) throws Exception {
        String[] a = line.split(" ");
        Minecraft mc = Minecraft.getInstance();
        Makeium.LOGGER.info("Makeium dev test: {}", line);
        switch (a[0]) {
            case "sel" -> {
                Selection.setA(new BlockPos(i(a[1]), i(a[2]), i(a[3])));
                Selection.setB(new BlockPos(i(a[4]), i(a[5]), i(a[6])));
                return 2;
            }
            case "edit" -> {
                CompoundTag d = a[1].equals("undo") || a[1].equals("paste") || a[1].equals("rotate") || a[1].equals("mirror") ? new CompoundTag() : Selection.toTag();
                d.putString("op", a[1]);
                if (a.length > 2) {
                    switch (a[1]) {
                        case "fill", "walls" -> d.putString("block", a[2]);
                        case "replace" -> {
                            d.putString("from", a[2]);
                            d.putString("to", a[3]);
                        }
                        case "move" -> d.putIntArray("by", new int[]{i(a[2]), i(a[3]), i(a[4])});
                        case "paste" -> d.putIntArray("at", new int[]{i(a[2]), i(a[3]), i(a[4])});
                        default -> {
                        }
                    }
                }
                Net.sendToServer("edit/op", d);
                return 10;
            }
            case "cpsave", "cprestore" -> {
                CompoundTag d = a[0].equals("cpsave") ? Selection.toTag() : new CompoundTag();
                d.putString("name", a[1]);
                d.putBoolean("entities", true);
                Net.sendToServer(a[0].equals("cpsave") ? "cp/save" : "cp/restore", d);
                return 20;
            }
            case "region" -> {
                // region <name> x1 y1 z1 x2 y2 z2
                var r = new RegionPack.Region(a[1], "minecraft:overworld", new int[]{i(a[2]), i(a[3]), i(a[4])}, new int[]{i(a[5]), i(a[6]), i(a[7])},
                        "@a", "say PASS_region_enter", "say PASS_region_leave", "");
                PackState.save(RegionPack.PACK, "test", RegionPack.compile(List.of(r)), new CompoundTag());
                return 80;
            }
            case "npc" -> {
                var start = new NpcPack.DialogNode("start", "Guide", "Hello!", List.of(
                        new NpcPack.Choice("Next", "node", "second"),
                        new NpcPack.Choice("Say", "command", "say PASS_npc_choice")));
                var second = new NpcPack.DialogNode("second", "Guide", "Second page", List.of(new NpcPack.Choice("Bye", "close", "")));
                var npc = new NpcPack.Npc("guide", "Guide", "minecraft:villager", List.of(start, second));
                PackState.save(NpcPack.PACK, "test", NpcPack.compile(List.of(npc)), new CompoundTag());
                return 80;
            }
            case "camera" -> {
                var keys = List.of(new CameraPack.Key(0.5, 125, -6, 0, 20, 20), new CameraPack.Key(6, 126, 0.5, 90, 20, 20), new CameraPack.Key(0.5, 127, 6, 180, 30, 20));
                PackState.save(CameraPack.PACK, "test", CameraPack.compile(List.of(new CameraPack.Cutscene("tour", keys))), new CompoundTag());
                return 80;
            }
            case "link" -> {
                PackState.save(LinkPack.PACK, "test", LinkPack.compile(List.of(new LinkPack.Link("door", "makeium_regions:tick"))), new CompoundTag());
                return 80;
            }
            case "cmdblock" -> {
                BlockPos p = new BlockPos(i(a[1]), i(a[2]), i(a[3]));
                if (mc.level.getBlockEntity(p) instanceof CommandBlockEntity be) {
                    mc.player.openCommandBlock(be);
                } else {
                    Makeium.LOGGER.warn("Makeium dev test: no command block at {}", p);
                }
                return 20;
            }
            case "rightclick" -> {
                BlockPos p = new BlockPos(i(a[1]), i(a[2]), i(a[3]));
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p), net.minecraft.core.Direction.UP, p, false);
                mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                return 20;
            }
            case "cmdblock_done" -> {
                if (mc.gui.screen() instanceof CommandBlockScreen s) s.devSetAndDone(line.substring("cmdblock_done ".length()));
                else Makeium.LOGGER.warn("Makeium dev test: the Makeium command block editor is not open ({})", mc.gui.screen());
                return 10;
            }
            case "pixel" -> {
                RpProject rp = RpProject.open(a[1]);
                NativeImage img = PixelEditorScreen.blank(16, 16);
                PixelEditorScreen s = new PixelEditorScreen(null, rp, "assets/minecraft/textures/block/dirt.png", img, () -> {});
                mc.gui.setScreen(s);
                return 5;
            }
            case "pixel_save" -> {
                if (mc.gui.screen() instanceof PixelEditorScreen s) s.devPaintAndSave();
                return 80;
            }
            case "item" -> {
                ResourcePackScreen.devItem(a[1], "minecraft:paper", "Test Item");
                return 80;
            }
            case "package" -> {
                CompoundTag d = new CompoundTag();
                d.putString("name", "playtest");
                Net.sendToServer("pkg/export", d);
                return 100;
            }
            case "templates" -> {
                CompoundTag files = new CompoundTag();
                for (String type : com.hyproj.makeium.feature.datapack.JsonTemplates.types(List.of())) {
                    String path = com.hyproj.makeium.feature.datapack.JsonTemplates.path("tpl", type, "sample");
                    files.putString(path, com.hyproj.makeium.feature.datapack.JsonTemplates.template(type).replace("my_map:", "tpl:"));
                }
                com.hyproj.makeium.client.Replies.on("dp/validate", r -> {
                    var problems = r.getListOrEmpty("problems");
                    Makeium.LOGGER.info("Makeium dev test: templates -> {} problems", problems.size());
                    for (var t : problems) Makeium.LOGGER.warn("TEMPLATE_PROBLEM {}", t.asString().orElse(""));
                });
                CompoundTag d = new CompoundTag();
                d.put("files", files);
                Net.sendToServer("dp/validate", d);
                return 20;
            }
            default -> Makeium.LOGGER.warn("Makeium dev test: unknown '{}'", a[0]);
        }
        return 0;
    }

    private static int i(String s) {
        return Integer.parseInt(s);
    }
}
