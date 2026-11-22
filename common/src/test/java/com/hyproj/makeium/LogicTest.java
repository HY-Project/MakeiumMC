package com.hyproj.makeium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import com.hyproj.makeium.feature.datapack.BlockCompiler;
import com.hyproj.makeium.feature.datapack.BlockDefs;
import com.hyproj.makeium.feature.datapack.Node;
import com.hyproj.makeium.feature.datapack.Project;
import com.hyproj.makeium.feature.datapack.Script;
import com.hyproj.makeium.feature.display.WallSnap;
import com.hyproj.makeium.feature.pack.PackageRules;
import com.hyproj.makeium.server.DatapackStore;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class LogicTest {
    // ---------------------------------------------------------------- wall snap

    /** The display's facing (local +Z) after vanilla's orientation, for a placement. */
    private static Vector3f facing(WallSnap.Placement p) {
        Quaternionf q = new Quaternionf().rotationYXZ((float) Math.toRadians(-p.yaw()), (float) Math.toRadians(p.pitch()), 0f);
        return q.transform(new Vector3f(0, 0, 1));
    }

    @Test
    void wallTextFacesOutOfEveryWall() {
        for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            WallSnap.Placement p = WallSnap.place(WallSnap.Kind.TEXT, face, 0.5 + face.getStepX() * 0.5, 64.3, 0.5 + face.getStepZ() * 0.5,
                    0, 64, 0, 0f, WallSnap.Snap.CENTER, 0, 9, 1f);
            Vector3f f = facing(p);
            assertEquals(face.getStepX(), f.x, 1e-4, face + " facing x");
            assertEquals(face.getStepZ(), f.z, 1e-4, face + " facing z");
            assertEquals(0, f.y, 1e-4);
            // sits just in front of the face, centred on the block face
            double plane = face.getAxis() == Direction.Axis.X ? p.x() : p.z();
            double expected = 0.5 + 0.5 * (face.getAxis() == Direction.Axis.X ? face.getStepX() : face.getStepZ()) + WallSnap.GAP * (face.getAxis() == Direction.Axis.X ? face.getStepX() : face.getStepZ());
            assertEquals(expected, plane, 1e-9);
            assertEquals(64.5, p.y(), 1e-9);
            // centred vertically: shifted down by half of (9 + 1) px
            assertEquals(-5 * WallSnap.TEXT_PIXEL, p.ty(), 1e-6);
        }
    }

    @Test
    void floorAndCeilingTextFaceUpAndDownAndReadUpright() {
        WallSnap.Placement floor = WallSnap.place(WallSnap.Kind.TEXT, Direction.UP, 0.3, 65, 0.7, 0, 64, 0, 180f, WallSnap.Snap.CENTER, 0, 9, 1f);
        Vector3f f = facing(floor);
        assertEquals(1, f.y, 1e-4);
        assertEquals(65 + WallSnap.GAP, floor.y(), 1e-9);
        // player looks north (yaw 180): text top (local +Y) must point north (−Z) so it reads upright
        Quaternionf q = new Quaternionf().rotationYXZ((float) Math.toRadians(-floor.yaw()), (float) Math.toRadians(floor.pitch()), 0f);
        Vector3f top = q.transform(new Vector3f(0, 1, 0));
        assertEquals(-1, top.z, 1e-4);
        Vector3f right = q.transform(new Vector3f(1, 0, 0));
        assertEquals(1, right.x, 1e-4, "reading direction east for a player facing north");

        WallSnap.Placement ceil = WallSnap.place(WallSnap.Kind.TEXT, Direction.DOWN, 0.3, 64, 0.7, 0, 64, 0, 180f, WallSnap.Snap.CENTER, 0, 9, 1f);
        assertEquals(-1, facing(ceil).y, 1e-4);
        Quaternionf qc = new Quaternionf().rotationYXZ((float) Math.toRadians(-ceil.yaw()), (float) Math.toRadians(ceil.pitch()), 0f);
        // looking straight up while facing north, the top of the view is south and the right is east
        assertEquals(1, qc.transform(new Vector3f(0, 1, 0)).z, 1e-4);
        assertEquals(1, qc.transform(new Vector3f(1, 0, 0)).x, 1e-4);
    }

    @Test
    void rolledWallTextStaysCentred() {
        for (int turns = 0; turns < 4; turns++) {
            WallSnap.Placement p = WallSnap.place(WallSnap.Kind.TEXT, Direction.SOUTH, 0.5, 64.5, 1, 0, 64, 0, 0f, WallSnap.Snap.CENTER, turns, 29, 2f);
            // text centre in local space: T · Rz(roll) · (0, h/2, 0)
            double hh = (29 + 1) / 2.0 * WallSnap.TEXT_PIXEL * 2f;
            Matrix4f m = new Matrix4f().translation(p.tx(), p.ty(), p.tz()).rotateZ((float) Math.toRadians(p.roll()));
            Vector3f c = m.transformPosition(new Vector3f(0, (float) hh, 0));
            assertEquals(0, c.x, 1e-5, "turns " + turns);
            assertEquals(0, c.y, 1e-5, "turns " + turns);
        }
    }

    @Test
    void blockDisplayGoesInTheNeighbourCell() {
        WallSnap.Placement p = WallSnap.place(WallSnap.Kind.BLOCK, Direction.EAST, 11, 64.2, 5.5, 10, 64, 5, 0f, WallSnap.Snap.CENTER, 0, 0, 1f);
        assertEquals(11, p.x());
        assertEquals(64, p.y());
        assertEquals(5, p.z());
    }

    @Test
    void gridSnapRoundsToSixteenths() {
        WallSnap.Placement p = WallSnap.place(WallSnap.Kind.TEXT, Direction.NORTH, 3.37, 70.81, 2, 3, 70, 2, 0f, WallSnap.Snap.GRID, 0, 9, 1f);
        assertEquals(Math.round(3.37 * 16) / 16.0, p.x(), 1e-9);
        assertEquals(Math.round(70.81 * 16) / 16.0, p.y(), 1e-9);
    }

    // ---------------------------------------------------------------- block compiler

    private static Node node(String def, String... kv) {
        Node n = Node.create(BlockDefs.get(def));
        for (int i = 0; i + 1 < kv.length; i += 2) n.values.put(kv[i], kv[i + 1]);
        return n;
    }

    @Test
    void loadScriptCompilesToTaggedFunction() {
        Project p = new Project();
        p.namespace = "Test Map";
        Script s = new Script(0, 0);
        s.nodes.add(node("ev_load"));
        s.nodes.add(node("say", "msg", "hi"));
        s.nodes.add(node("tp", "who", "@a", "pos", "0 64 0"));
        p.scripts.add(s);
        Map<String, String> files = BlockCompiler.compile(p).files();
        assertEquals("say hi\ntp @a 0 64 0\n", files.get("data/test_map/function/mk/event/load_0.mcfunction"));
        assertTrue(files.get("data/minecraft/tags/function/load.json").contains("test_map:mk/event/load_0"));
    }

    @Test
    void cBlocksInlineOneLineAndSplitMore() {
        Project p = new Project();
        p.namespace = "m";
        Script s = new Script(0, 0);
        s.nodes.add(node("ev_tick"));
        Node as = node("as_at", "who", "@a[tag=x]");
        as.body.add(node("say", "msg", "one"));
        s.nodes.add(as);
        Node iff = node("if", "cond", "entity @s[tag=y]");
        iff.body.add(node("say", "msg", "a"));
        iff.body.add(node("say", "msg", "b"));
        s.nodes.add(iff);
        p.scripts.add(s);
        Map<String, String> files = BlockCompiler.compile(p).files();
        String main = files.get("data/m/function/mk/event/tick_0.mcfunction");
        assertTrue(main.startsWith("execute as @a[tag=x] at @s run say one\n"), main);
        assertTrue(main.contains("execute if entity @s[tag=y] run function m:mk/gen/"), main);
        String gen = files.entrySet().stream().filter(e -> e.getKey().contains("/mk/gen/")).findFirst().orElseThrow().getValue();
        assertEquals("say a\nsay b\n", gen);
    }

    @Test
    void optionalPartsAndWaitAndRepeat() {
        Project p = new Project();
        p.namespace = "m";
        Script s = new Script(0, 0);
        s.nodes.add(node("ev_function", "name", "door/open"));
        s.nodes.add(node("clear", "who", "@s", "item", "", "count", ""));
        Node rep = node("repeat", "times", "3");
        rep.body.add(node("say", "msg", "x"));
        s.nodes.add(rep);
        s.nodes.add(node("wait", "ticks", "40"));
        s.nodes.add(node("say", "msg", "later"));
        p.scripts.add(s);
        Map<String, String> files = BlockCompiler.compile(p).files();
        String main = files.get("data/m/function/door/open.mcfunction");
        assertTrue(main.startsWith("clear @s\n"), main);
        assertTrue(main.contains("scoreboard players set #door_open_r1 m_tmp 3"), main);
        assertTrue(main.contains("schedule function m:mk/gen/"), main);
        assertFalse(main.contains("later"), "blocks after wait run from the scheduled function");
        assertTrue(files.values().stream().anyMatch(v -> v.equals("say later\n")));
        assertTrue(files.get("data/m/function/mk/load.mcfunction").contains("scoreboard objectives add m_tmp dummy"));
    }

    @Test
    void interactEventMakesAnAdvancement() {
        Project p = new Project();
        p.namespace = "m";
        Script s = new Script(0, 0);
        s.nodes.add(node("ev_interact", "tag", "lever1"));
        s.nodes.add(node("say", "msg", "clicked"));
        p.scripts.add(s);
        Map<String, String> files = BlockCompiler.compile(p).files();
        String adv = files.get("data/m/advancement/mk/event/interact_0.json");
        assertTrue(adv.contains("minecraft:player_interacted_with_entity") && adv.contains("lever1") && adv.contains("m:mk/event/interact_0"), adv);
        assertTrue(files.get("data/m/function/mk/event/interact_0.mcfunction").startsWith("advancement revoke @s only m:mk/event/interact_0\n"));
    }

    @Test
    void projectRoundTripsThroughJson() {
        Project p = new Project();
        p.namespace = "ns";
        Script s = new Script(12, 34);
        s.nodes.add(node("ev_tick"));
        Node ie = node("if_else", "cond", "score @s x matches 1..");
        ie.body.add(node("say", "msg", "yes"));
        ie.elseBody.add(node("say", "msg", "no"));
        s.nodes.add(ie);
        p.scripts.add(s);
        Project q = Project.fromJson(p.toJson());
        assertEquals(BlockCompiler.compile(p).files(), BlockCompiler.compile(q).files());
        assertEquals(12, q.scripts.get(0).x);
    }

    @Test
    void importedPackBecomesRawScripts() {
        Project p = Project.fromPackFiles("pack", Map.of("data/abc/function/hello.mcfunction", "# greet\nsay hello\n", "pack.mcmeta", "{}"));
        assertEquals("abc", p.namespace);
        assertEquals("data/abc/function/hello.mcfunction", BlockCompiler.compile(p).files().keySet().iterator().next());
        assertEquals("# greet\nsay hello\n", BlockCompiler.compile(p).files().get("data/abc/function/hello.mcfunction"));
    }

    // ---------------------------------------------------------------- sanitising

    @Test
    void datapackPathsCannotEscapeThePack() {
        Path pack = Path.of("/tmp/world/datapacks/my_pack").toAbsolutePath();
        assertThrows(IllegalArgumentException.class, () -> DatapackStore.file(pack, "../level.dat"));
        assertThrows(IllegalArgumentException.class, () -> DatapackStore.file(pack, "/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> DatapackStore.file(pack, "data/x/../../../a.json"));
        assertThrows(IllegalArgumentException.class, () -> DatapackStore.file(pack, "data\\..\\x"));
        assertTrue(DatapackStore.file(pack, "data/x/function/a.mcfunction").startsWith(pack));
        assertFalse(DatapackStore.PACK_NAME.matcher("a/b").matches());
    }

    @Test
    void packagingSkipsPlayerAndEditorData() {
        assertTrue(PackageRules.skip("players/data/abc.dat"));
        assertTrue(PackageRules.skip("session.lock"));
        assertTrue(PackageRules.skip("makeium/checkpoints/a.nbt"));
        assertTrue(PackageRules.skip("datapacks/x/makeium/project.json"));
        assertFalse(PackageRules.skip("region/r.0.0.mca"));
        assertFalse(PackageRules.skip("level.dat"));
        assertFalse(PackageRules.skip("datapacks/x/data/x/function/a.mcfunction"));
    }
}
