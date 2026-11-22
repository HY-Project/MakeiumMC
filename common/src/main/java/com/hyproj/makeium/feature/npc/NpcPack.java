package com.hyproj.makeium.feature.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hyproj.makeium.feature.datapack.BlockCompiler;

/**
 * NPCs with dialog trees → the "makeium_npc" datapack (26.3 dialogs).
 *
 * <ul>
 *   <li>Each node is a {@code multi_action} dialog; each choice runs {@code /trigger mk_dialog set <id>} — trigger works
 *       for every player, unlike /function which needs operator rights inside dialogs.</li>
 *   <li>A per-tick dispatcher maps the id to the choice's action: open another node, run a function or command,
 *       close — and always calls {@code #makeium:dialog/<id>} so block-coding scripts can react.</li>
 *   <li>Talking: an interaction entity tagged {@code mk_npc_<name>} sits on the NPC; clicking it grants an advancement
 *       whose reward opens the start node.</li>
 * </ul>
 */
public final class NpcPack {
    public static final String PACK = "makeium_npc";
    public static final String NS = "makeium_npc";
    public static final String OBJECTIVE = "mk_dialog";

    public record Choice(String label, String action, String target) {
        // action: "node" (target = node id), "function" (target = function id), "command" (target = command), "close"
    }

    public record DialogNode(String id, String title, String body, List<Choice> choices) {}

    public record Npc(String name, String displayName, String entityType, List<DialogNode> nodes) {}

    private NpcPack() {}

    public static String safe(String s) {
        return BlockCompiler.sanitizePath(s).replace('/', '_');
    }

    /** The dispatcher id of choice {@code ci} of node {@code ni} of npc {@code npcIndex} (stable while the tree is). */
    public static int choiceId(int npcIndex, int ni, int ci) {
        return 1 + npcIndex * 10_000 + ni * 100 + ci;
    }

    public static Map<String, String> compile(List<Npc> npcs) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> load = new ArrayList<>(List.of("scoreboard objectives add " + OBJECTIVE + " trigger"));
        List<String> dispatch = new ArrayList<>();
        for (int i = 0; i < npcs.size(); i++) {
            Npc npc = npcs.get(i);
            String n = safe(npc.name());
            for (int ni = 0; ni < npc.nodes().size(); ni++) {
                DialogNode node = npc.nodes().get(ni);
                JsonObject d = new JsonObject();
                d.addProperty("type", "minecraft:multi_action");
                d.add("title", text(node.title().isBlank() ? npc.displayName() : node.title()));
                JsonObject body = new JsonObject();
                body.addProperty("type", "minecraft:plain_message");
                body.add("contents", text(node.body()));
                d.add("body", body);
                JsonArray actions = new JsonArray();
                for (int ci = 0; ci < node.choices().size(); ci++) {
                    Choice c = node.choices().get(ci);
                    int id = choiceId(i, ni, ci);
                    JsonObject a = new JsonObject();
                    a.add("label", text(c.label()));
                    JsonObject act = new JsonObject();
                    act.addProperty("type", "minecraft:run_command");
                    act.addProperty("command", "trigger " + OBJECTIVE + " set " + id);
                    a.add("action", act);
                    actions.add(a);
                    String then = switch (c.action()) {
                        case "node" -> "function " + NS + ":" + n + "/node/" + safe(c.target());
                        case "function" -> "function " + c.target().strip();
                        case "command" -> c.target().strip().startsWith("/") ? c.target().strip().substring(1) : c.target().strip();
                        default -> "dialog clear @s";
                    };
                    if (!then.isBlank()) dispatch.add("execute if score @s " + OBJECTIVE + " matches " + id + " run " + then);
                    dispatch.add("execute if score @s " + OBJECTIVE + " matches " + id + " run function #makeium:dialog/" + id);
                    out.put("data/makeium/tags/function/dialog/" + id + ".json", "{\"replace\":false,\"values\":[]}");
                }
                if (actions.isEmpty()) {
                    JsonObject a = new JsonObject();
                    a.add("label", text("OK"));
                    actions.add(a);
                }
                d.add("actions", actions);
                d.addProperty("columns", Math.max(1, Math.min(3, actions.size())));
                d.addProperty("can_close_with_escape", true);
                d.addProperty("pause", false);
                // Dialogs are a world registry (read only when the world loads), so nodes are shown inline: the pack
                // works right after /reload. JSON is valid SNBT.
                out.put("data/" + NS + "/function/" + n + "/node/" + safe(node.id()) + ".mcfunction", "dialog show @s " + d + "\n");
            }
            // talking: advancement → open the first node
            String start = npc.nodes().isEmpty() ? "start" : safe(npc.nodes().get(0).id());
            JsonObject adv = new JsonObject();
            JsonObject crit = new JsonObject();
            JsonObject c = new JsonObject();
            c.addProperty("trigger", "minecraft:player_interacted_with_entity");
            c.add("conditions", com.hyproj.makeium.feature.datapack.Predicates.interactedWithTagged("mk_npc_" + n));
            crit.add("talk", c);
            adv.add("criteria", crit);
            JsonObject rewards = new JsonObject();
            rewards.addProperty("function", NS + ":" + n + "/talk");
            adv.add("rewards", rewards);
            out.put("data/" + NS + "/advancement/" + n + "/talk.json", adv.toString());
            out.put("data/" + NS + "/function/" + n + "/talk.mcfunction",
                    "advancement revoke @s only " + NS + ":" + n + "/talk\nfunction " + NS + ":" + n + "/node/" + start + "\n");
            out.put("data/" + NS + "/function/" + n + "/spawn.mcfunction", spawnCommands(npc) + "\n");
        }
        List<String> tick = new ArrayList<>();
        tick.add("execute as @a[scores={" + OBJECTIVE + "=1..}] at @s run function " + NS + ":dispatch");
        tick.add("scoreboard players reset @a[scores={" + OBJECTIVE + "=1..}] " + OBJECTIVE);
        tick.add("scoreboard players enable @a " + OBJECTIVE);
        out.put("data/" + NS + "/function/dispatch.mcfunction", dispatch.isEmpty() ? "# no choices\n" : String.join("\n", dispatch) + "\n");
        out.put("data/" + NS + "/function/tick.mcfunction", String.join("\n", tick) + "\n");
        out.put("data/" + NS + "/function/load.mcfunction", String.join("\n", load) + "\n");
        out.put("data/minecraft/tags/function/tick.json", "{\"values\":[\"" + NS + ":tick\"]}");
        out.put("data/minecraft/tags/function/load.json", "{\"values\":[\"" + NS + ":load\"]}");
        return out;
    }

    /** Commands that summon the NPC (and its click box) at the executing position. */
    public static String spawnCommands(Npc npc) {
        String n = safe(npc.name());
        String type = npc.entityType() == null || npc.entityType().isBlank() ? "minecraft:villager" : npc.entityType();
        String name = new com.google.gson.JsonPrimitive(npc.displayName()).toString();
        String common = "Tags:[\"mk_npc_body_" + n + "\"],CustomName:" + name + ",CustomNameVisible:1b,PersistenceRequired:1b,Invulnerable:1b,Silent:1b,NoAI:1b";
        String body = type.equals("minecraft:villager") ? "{" + common + ",VillagerData:{profession:\"minecraft:librarian\",level:2,type:\"minecraft:plains\"}}"
                : type.equals("minecraft:armor_stand") ? "{" + common.replace(",NoAI:1b", "") + ",ShowArms:1b}" : "{" + common + "}";
        return "kill @e[tag=mk_npc_body_" + n + "]\nkill @e[tag=mk_npc_" + n + "]\n"
                + "summon " + type + " ~ ~ ~ " + body + "\n"
                + "summon minecraft:interaction ~ ~ ~ {width:0.9f,height:2.0f,response:1b,Tags:[\"mk_npc_" + n + "\"]}";
    }

    private static JsonObject text(String s) {
        JsonObject o = new JsonObject();
        o.addProperty("text", s == null ? "" : s);
        return o;
    }
}
