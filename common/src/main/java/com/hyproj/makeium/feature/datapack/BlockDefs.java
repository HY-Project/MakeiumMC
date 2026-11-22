package com.hyproj.makeium.feature.datapack;

import static com.hyproj.makeium.feature.datapack.SlotDef.Type.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hyproj.makeium.feature.datapack.BlockDef.Category;
import com.hyproj.makeium.feature.datapack.BlockDef.Shape;

/**
 * The block palette. Templates use {@code {slot}} placeholders, {@code {body}} for a C block's body, and
 * {@code [[ ... ]]} for an optional part that is dropped when any slot inside it is blank.
 *
 * <p>Besides these hand-made blocks, every root command the server offers is available as a generated block
 * ({@code cmd/<name>}, see {@link #command}), so all 26.3 commands are covered even without a dedicated block.
 */
public final class BlockDefs {
    private static final Map<String, BlockDef> DEFS = new LinkedHashMap<>();

    public static final String[] COLORS = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
    public static final String[] SOUND_SOURCES = {"master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice", "ui"};

    static {
        // ---- events (hats) ----
        hat("ev_load");
        hat("ev_tick");
        hat("ev_every", SlotDef.of("ticks", NUMBER, "20"));
        hat("ev_function", SlotDef.of("name", TEXT, "my_function"));
        hat("ev_join");
        hat("ev_first_join");
        hat("ev_death");
        hat("ev_trigger", SlotDef.of("objective", OBJECTIVE, "menu"));
        hat("ev_interact", SlotDef.of("tag", TEXT, "my_button"));
        hat("ev_region", SlotDef.of("region", TEXT, "spawn"), SlotDef.choice("mode", "enter", "enter", "leave", "inside"));
        hat("ev_dialog", SlotDef.of("choice", NUMBER, "1"));
        hat("ev_advancement", SlotDef.registry("trigger", "trigger_type", "minecraft:location"), SlotDef.of("conditions", NBT, "{}"));

        // ---- control ----
        c("if", Category.CONTROL, "execute if {cond} run {body}", SlotDef.of("cond", CONDITION, "entity @s[tag=vip]"));
        c("unless", Category.CONTROL, "execute unless {cond} run {body}", SlotDef.of("cond", CONDITION, "entity @s[tag=vip]"));
        def("if_else", Category.CONTROL, Shape.C2, "", SlotDef.of("cond", CONDITION, "entity @s[tag=vip]"));
        c("as_at", Category.CONTROL, "execute as {who} at @s run {body}", SlotDef.of("who", SELECTOR, "@a"));
        c("as", Category.CONTROL, "execute as {who} run {body}", SlotDef.of("who", SELECTOR, "@a"));
        c("at", Category.CONTROL, "execute at {who} run {body}", SlotDef.of("who", SELECTOR, "@s"));
        c("positioned", Category.CONTROL, "execute positioned {pos} run {body}", SlotDef.of("pos", POS, "~ ~1 ~"));
        c("in_dim", Category.CONTROL, "execute in {dim} run {body}", SlotDef.choice("dim", "minecraft:overworld", "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
        def("repeat", Category.CONTROL, Shape.C, "", SlotDef.of("times", NUMBER, "3"));
        def("while", Category.CONTROL, Shape.C, "", SlotDef.of("cond", CONDITION, "entity @s[tag=moving]"));
        def("chance", Category.CONTROL, Shape.C, "", SlotDef.of("chance", NUMBER, "0.5"));
        def("wait", Category.CONTROL, Shape.STATEMENT, "", SlotDef.of("ticks", NUMBER, "20"));
        st("stop", Category.CONTROL, "return 0");

        // ---- player ----
        st("tp", Category.PLAYER, "tp {who} {pos}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("pos", POS, "~ ~ ~"));
        st("tp_facing", Category.PLAYER, "tp {who} {pos} facing {look}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("pos", POS, "~ ~ ~"), SlotDef.of("look", POS, "~ ~ ~5"));
        st("tp_to", Category.PLAYER, "tp {who} {target}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("target", SELECTOR, "@p"));
        st("give", Category.PLAYER, "give {who} {item} {count}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("item", ITEM, "minecraft:diamond"), SlotDef.of("count", NUMBER, "1"));
        st("clear", Category.PLAYER, "clear {who}[[ {item}]][[ {count}]]", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("item", ITEM, ""), SlotDef.of("count", NUMBER, ""));
        st("gamemode", Category.PLAYER, "gamemode {mode} {who}", SlotDef.choice("mode", "adventure", "survival", "creative", "adventure", "spectator"), SlotDef.of("who", SELECTOR, "@s"));
        st("xp", Category.PLAYER, "xp add {who} {amount} {unit}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("amount", NUMBER, "5"), SlotDef.choice("unit", "levels", "points", "levels"));
        st("spawnpoint", Category.PLAYER, "spawnpoint {who} {pos}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("pos", POS, "~ ~ ~"));
        st("effect", Category.PLAYER, "effect give {who} {effect} {seconds} {amp} {hide}", SlotDef.of("who", SELECTOR, "@s"),
                SlotDef.registry("effect", "mob_effect", "minecraft:speed"), SlotDef.of("seconds", NUMBER, "10"), SlotDef.of("amp", NUMBER, "0"), SlotDef.choice("hide", "true", "true", "false"));
        st("effect_clear", Category.PLAYER, "effect clear {who}[[ {effect}]]", SlotDef.of("who", SELECTOR, "@s"), SlotDef.registry("effect", "mob_effect", ""));
        st("heal", Category.PLAYER, "effect give {who} minecraft:instant_health 1 10 true", SlotDef.of("who", SELECTOR, "@s"));
        st("feed", Category.PLAYER, "effect give {who} minecraft:saturation 1 10 true", SlotDef.of("who", SELECTOR, "@s"));
        st("attribute", Category.PLAYER, "attribute {who} {attr} base set {value}", SlotDef.of("who", SELECTOR, "@s"),
                SlotDef.registry("attr", "attribute", "minecraft:max_health"), SlotDef.of("value", NUMBER, "40"));
        st("kill", Category.PLAYER, "kill {who}", SlotDef.of("who", SELECTOR, "@e[type=minecraft:zombie,distance=..10]"));
        st("damage", Category.PLAYER, "damage {who} {amount}[[ {type}]]", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("amount", NUMBER, "4"), SlotDef.of("type", TEXT, "minecraft:generic"));

        // ---- entities ----
        st("summon", Category.ENTITY, "summon {type} {pos}[[ {nbt}]]", SlotDef.registry("type", "entity_type", "minecraft:zombie"), SlotDef.of("pos", POS, "~ ~ ~"), SlotDef.of("nbt", NBT, ""));
        st("summon_interaction", Category.ENTITY, "summon minecraft:interaction {pos} {width:1.01f,height:1.01f,Tags:[\"{tag}\"]}", SlotDef.of("pos", POS, "~ ~ ~"), SlotDef.of("tag", TEXT, "my_button"));
        st("tag_add", Category.ENTITY, "tag {who} add {tag}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("tag", TEXT, "vip"));
        st("tag_remove", Category.ENTITY, "tag {who} remove {tag}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("tag", TEXT, "vip"));
        st("ride", Category.ENTITY, "ride {who} mount {vehicle}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("vehicle", SELECTOR, "@e[type=minecraft:horse,limit=1,sort=nearest]"));
        st("dismount", Category.ENTITY, "ride {who} dismount", SlotDef.of("who", SELECTOR, "@s"));
        st("data_entity", Category.ENTITY, "data merge entity {who} {nbt}", SlotDef.of("who", SELECTOR, "@e[tag=npc,limit=1]"), SlotDef.of("nbt", NBT, "{NoAI:1b}"));
        st("rotate", Category.ENTITY, "rotate {who} {yaw} {pitch}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("yaw", NUMBER, "0"), SlotDef.of("pitch", NUMBER, "0"));
        st("glow", Category.ENTITY, "effect give {who} minecraft:glowing {seconds} 0 true", SlotDef.of("who", SELECTOR, "@e[tag=target]"), SlotDef.of("seconds", NUMBER, "5"));

        // ---- world ----
        st("setblock", Category.WORLD, "setblock {pos} {block}[[ {mode}]]", SlotDef.of("pos", POS, "~ ~ ~"), SlotDef.of("block", BLOCK, "minecraft:stone"), SlotDef.choice("mode", "replace", "replace", "destroy", "keep"));
        st("fill", Category.WORLD, "fill {from} {to} {block}[[ {mode}]]", SlotDef.of("from", POS, "~-2 ~ ~-2"), SlotDef.of("to", POS, "~2 ~ ~2"), SlotDef.of("block", BLOCK, "minecraft:glass"),
                SlotDef.choice("mode", "replace", "replace", "destroy", "hollow", "keep", "outline"));
        st("clone", Category.WORLD, "clone {from} {to} {dest}", SlotDef.of("from", POS, "0 64 0"), SlotDef.of("to", POS, "5 70 5"), SlotDef.of("dest", POS, "10 64 0"));
        st("time", Category.WORLD, "time set {time}", SlotDef.choice("time", "day", "day", "noon", "night", "midnight"));
        st("weather", Category.WORLD, "weather {kind}", SlotDef.choice("kind", "clear", "clear", "rain", "thunder"));
        st("gamerule", Category.WORLD, "gamerule {rule} {value}", SlotDef.registry("rule", "game_rule", "minecraft:keep_inventory"), SlotDef.of("value", TEXT, "true"));
        st("difficulty", Category.WORLD, "difficulty {level}", SlotDef.choice("level", "normal", "peaceful", "easy", "normal", "hard"));
        st("worldborder", Category.WORLD, "worldborder set {size}[[ {seconds}]]", SlotDef.of("size", NUMBER, "200"), SlotDef.of("seconds", NUMBER, ""));
        st("lightning", Category.WORLD, "summon minecraft:lightning_bolt {pos}", SlotDef.of("pos", POS, "~ ~ ~"));
        st("place_template", Category.WORLD, "place template {id} {pos}", SlotDef.of("id", TEXT, "minecraft:my_structure"), SlotDef.of("pos", POS, "~ ~ ~"));
        st("data_block", Category.WORLD, "data merge block {pos} {nbt}", SlotDef.of("pos", POS, "~ ~-1 ~"), SlotDef.of("nbt", NBT, "{}"));

        // ---- items ----
        st("item_replace", Category.ITEMS, "item replace entity {who} {slot} with {item}[[ {count}]]", SlotDef.of("who", SELECTOR, "@s"),
                SlotDef.choice("slot", "weapon.mainhand", "weapon.mainhand", "weapon.offhand", "armor.head", "armor.chest", "armor.legs", "armor.feet", "hotbar.0", "inventory.0"),
                SlotDef.of("item", ITEM, "minecraft:iron_sword"), SlotDef.of("count", NUMBER, ""));
        st("loot_give", Category.ITEMS, "loot give {who} loot {table}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("table", TEXT, "minecraft:chests/simple_dungeon"));
        st("enchant", Category.ITEMS, "enchant {who} {ench}[[ {level}]]", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("ench", TEXT, "minecraft:sharpness"), SlotDef.of("level", NUMBER, "1"));
        st("item_drop", Category.ITEMS, "summon minecraft:item {pos} {Item:{id:\"{item}\",count:{count}}}", SlotDef.of("pos", POS, "~ ~1 ~"), SlotDef.registry("item", "item", "minecraft:apple"), SlotDef.of("count", NUMBER, "1"));

        // ---- scoreboard / teams ----
        st("obj_add", Category.SCORE, "scoreboard objectives add {obj} {criteria}[[ {name}]]", SlotDef.of("obj", OBJECTIVE, "points"), SlotDef.of("criteria", TEXT, "dummy"), SlotDef.of("name", RICH_TEXT, ""));
        st("score_set", Category.SCORE, "scoreboard players set {who} {obj} {value}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("obj", OBJECTIVE, "points"), SlotDef.of("value", NUMBER, "0"));
        st("score_add", Category.SCORE, "scoreboard players add {who} {obj} {value}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("obj", OBJECTIVE, "points"), SlotDef.of("value", NUMBER, "1"));
        st("score_remove", Category.SCORE, "scoreboard players remove {who} {obj} {value}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("obj", OBJECTIVE, "points"), SlotDef.of("value", NUMBER, "1"));
        st("score_reset", Category.SCORE, "scoreboard players reset {who}[[ {obj}]]", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("obj", OBJECTIVE, ""));
        st("score_op", Category.SCORE, "scoreboard players operation {who} {obj} {op} {src} {srcobj}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("obj", OBJECTIVE, "points"),
                SlotDef.choice("op", "+=", "=", "+=", "-=", "*=", "/=", "%=", "<", ">", "><"), SlotDef.of("src", SELECTOR, "@s"), SlotDef.of("srcobj", OBJECTIVE, "bonus"));
        st("score_display", Category.SCORE, "scoreboard objectives setdisplay {slot} {obj}", SlotDef.choice("slot", "sidebar", "sidebar", "list", "below_name"), SlotDef.of("obj", OBJECTIVE, "points"));
        st("trigger_enable", Category.SCORE, "scoreboard players enable {who} {obj}", SlotDef.of("who", SELECTOR, "@a"), SlotDef.of("obj", OBJECTIVE, "menu"));
        st("team_add", Category.SCORE, "team add {team}[[ {name}]]", SlotDef.of("team", TEXT, "red"), SlotDef.of("name", RICH_TEXT, ""));
        st("team_join", Category.SCORE, "team join {team} {who}", SlotDef.of("team", TEXT, "red"), SlotDef.of("who", SELECTOR, "@s"));
        st("team_color", Category.SCORE, "team modify {team} color {color}", SlotDef.of("team", TEXT, "red"), SlotDef.choice("color", "red", COLORS));
        st("team_ff", Category.SCORE, "team modify {team} friendlyFire {v}", SlotDef.of("team", TEXT, "red"), SlotDef.choice("v", "false", "true", "false"));

        // ---- text ----
        st("say", Category.TEXT, "say {msg}", SlotDef.of("msg", TEXT, "Hello!"));
        st("tellraw", Category.TEXT, "tellraw {who} {text}", SlotDef.of("who", SELECTOR, "@a"), SlotDef.of("text", RICH_TEXT, "{text:\"Hello\",color:\"green\"}"));
        st("title", Category.TEXT, "title {who} title {text}", SlotDef.of("who", SELECTOR, "@a"), SlotDef.of("text", RICH_TEXT, "{text:\"Welcome\",color:\"gold\"}"));
        st("subtitle", Category.TEXT, "title {who} subtitle {text}", SlotDef.of("who", SELECTOR, "@a"), SlotDef.of("text", RICH_TEXT, "{text:\"to the map\"}"));
        st("actionbar", Category.TEXT, "title {who} actionbar {text}", SlotDef.of("who", SELECTOR, "@a"), SlotDef.of("text", RICH_TEXT, "{text:\"Find the key\",color:\"yellow\"}"));
        st("title_times", Category.TEXT, "title {who} times {in} {stay} {out}", SlotDef.of("who", SELECTOR, "@a"), SlotDef.of("in", NUMBER, "10"), SlotDef.of("stay", NUMBER, "60"), SlotDef.of("out", NUMBER, "10"));
        st("title_clear", Category.TEXT, "title {who} clear", SlotDef.of("who", SELECTOR, "@a"));
        st("dialog_show", Category.TEXT, "dialog show {who} {dialog}", SlotDef.of("who", SELECTOR, "@s"), SlotDef.of("dialog", TEXT, "minecraft:server_links"));

        // ---- sound & particles ----
        st("playsound", Category.EFFECTS, "playsound {sound} {source} {who}[[ {pos}]]", SlotDef.registry("sound", "sound_event", "minecraft:entity.experience_orb.pickup"),
                SlotDef.choice("source", "master", SOUND_SOURCES), SlotDef.of("who", SELECTOR, "@a"), SlotDef.of("pos", POS, ""));
        st("stopsound", Category.EFFECTS, "stopsound {who}", SlotDef.of("who", SELECTOR, "@a"));
        st("particle", Category.EFFECTS, "particle {particle} {pos} {delta} {speed} {count}", SlotDef.registry("particle", "particle_type", "minecraft:happy_villager"),
                SlotDef.of("pos", POS, "~ ~1 ~"), SlotDef.of("delta", POS, "0.5 0.5 0.5"), SlotDef.of("speed", NUMBER, "0"), SlotDef.of("count", NUMBER, "20"));

        // ---- bossbar ----
        st("bossbar_add", Category.BOSSBAR, "bossbar add {id} {text}", SlotDef.of("id", TEXT, "minecraft:boss"), SlotDef.of("text", RICH_TEXT, "{text:\"Boss\",color:\"red\"}"));
        st("bossbar_players", Category.BOSSBAR, "bossbar set {id} players {who}", SlotDef.of("id", TEXT, "minecraft:boss"), SlotDef.of("who", SELECTOR, "@a"));
        st("bossbar_value", Category.BOSSBAR, "bossbar set {id} value {value}", SlotDef.of("id", TEXT, "minecraft:boss"), SlotDef.of("value", NUMBER, "100"));
        st("bossbar_max", Category.BOSSBAR, "bossbar set {id} max {value}", SlotDef.of("id", TEXT, "minecraft:boss"), SlotDef.of("value", NUMBER, "100"));
        st("bossbar_color", Category.BOSSBAR, "bossbar set {id} color {color}", SlotDef.of("id", TEXT, "minecraft:boss"), SlotDef.choice("color", "red", "blue", "green", "pink", "purple", "red", "white", "yellow"));
        st("bossbar_visible", Category.BOSSBAR, "bossbar set {id} visible {v}", SlotDef.of("id", TEXT, "minecraft:boss"), SlotDef.choice("v", "true", "true", "false"));
        st("bossbar_remove", Category.BOSSBAR, "bossbar remove {id}", SlotDef.of("id", TEXT, "minecraft:boss"));

        // ---- data / storage ----
        st("storage_set", Category.DATA, "data modify storage {storage} {path} set value {value}", SlotDef.of("storage", TEXT, "mymap:state"), SlotDef.of("path", TEXT, "door_open"), SlotDef.of("value", NBT, "1b"));
        st("storage_remove", Category.DATA, "data remove storage {storage} {path}", SlotDef.of("storage", TEXT, "mymap:state"), SlotDef.of("path", TEXT, "door_open"));
        st("store_score", Category.DATA, "execute store result score {who} {obj} run data get storage {storage} {path}", SlotDef.of("who", SELECTOR, "#global"),
                SlotDef.of("obj", OBJECTIVE, "points"), SlotDef.of("storage", TEXT, "mymap:state"), SlotDef.of("path", TEXT, "count"));

        // ---- functions ----
        st("call", Category.FUNCTIONS, "function {fn}", SlotDef.of("fn", FUNCTION, "mymap:my_function"));
        st("call_macro", Category.FUNCTIONS, "function {fn} {args}", SlotDef.of("fn", FUNCTION, "mymap:my_function"), SlotDef.of("args", NBT, "{name:\"Steve\"}"));
        st("schedule", Category.FUNCTIONS, "schedule function {fn} {ticks}t {mode}", SlotDef.of("fn", FUNCTION, "mymap:my_function"), SlotDef.of("ticks", NUMBER, "100"), SlotDef.choice("mode", "replace", "replace", "append"));
        st("schedule_clear", Category.FUNCTIONS, "schedule clear {fn}", SlotDef.of("fn", FUNCTION, "mymap:my_function"));
        st("return", Category.FUNCTIONS, "return {value}", SlotDef.of("value", NUMBER, "1"));
        def("comment", Category.FUNCTIONS, Shape.STATEMENT, "", SlotDef.of("text", TEXT, "note"));

        // ---- raw ----
        st("raw", Category.COMMANDS, "{command}", SlotDef.of("command", COMMAND, "say hi"));
    }


    private BlockDefs() {}

    private static void hat(String id, SlotDef... slots) {
        def(id, Category.EVENTS, Shape.HAT, "", slots);
    }

    private static void st(String id, Category c, String template, SlotDef... slots) {
        def(id, c, Shape.STATEMENT, template, slots);
    }

    private static void c(String id, Category c, String template, SlotDef... slots) {
        def(id, c, Shape.C, template, slots);
    }

    private static void def(String id, Category c, Shape shape, String template, SlotDef... slots) {
        DEFS.put(id, new BlockDef(id, c, shape, template, List.of(slots)));
    }

    /** A block by id, including generated {@code cmd/<root>} command blocks. */
    public static BlockDef get(String id) {
        BlockDef d = DEFS.get(id);
        if (d != null) return d;
        if (id.startsWith("cmd/")) return command(id.substring(4));
        return DEFS.get("raw");
    }

    /** A generated block for a root command: "<root> {args}". */
    public static BlockDef command(String root) {
        return new BlockDef("cmd/" + root, Category.COMMANDS, Shape.STATEMENT, root + "[[ {args}]]", List.of(SlotDef.of("args", COMMAND, "")));
    }

    public static List<BlockDef> inCategory(Category c) {
        List<BlockDef> out = new ArrayList<>();
        for (BlockDef d : DEFS.values()) if (d.category() == c) out.add(d);
        return out;
    }

    public static Iterable<BlockDef> all() {
        return DEFS.values();
    }
}
