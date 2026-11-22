package com.hyproj.makeium.feature.datapack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Starter content for every datapack file type. The type list is the server's own registry folders (all of 26.3's
 * data-driven registries, sent with dp/list) plus functions and tags; the common ones get a working example.
 */
public final class JsonTemplates {
    private static final Map<String, String> TEMPLATES = new LinkedHashMap<>();

    static {
        TEMPLATES.put("function", "# A function: one command per line\nsay Hello from Makeium!\n");
        TEMPLATES.put("recipe", """
                {
                  "type": "minecraft:crafting_shaped",
                  "category": "misc",
                  "pattern": [
                    "###",
                    "#X#",
                    "###"
                  ],
                  "key": {
                    "#": "minecraft:gold_ingot",
                    "X": "minecraft:apple"
                  },
                  "result": {
                    "id": "minecraft:golden_apple",
                    "count": 1
                  }
                }
                """);
        TEMPLATES.put("loot_table", """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": { "type": "minecraft:uniform", "min": 1, "max": 3 },
                      "entries": [
                        { "type": "minecraft:item", "name": "minecraft:diamond", "weight": 1 },
                        { "type": "minecraft:item", "name": "minecraft:iron_ingot", "weight": 5,
                          "modifier": { "type": "minecraft:set_count", "count": { "type": "minecraft:uniform", "min": 1, "max": 4 } } }
                      ]
                    }
                  ]
                }
                """);
        TEMPLATES.put("advancement", """
                {
                  "parent": "minecraft:adventure/root",
                  "display": {
                    "icon": { "id": "minecraft:map" },
                    "title": { "text": "Explorer" },
                    "description": { "text": "Find the secret room" },
                    "frame": "goal",
                    "show_toast": true,
                    "announce_to_chat": true
                  },
                  "criteria": {
                    "found": {
                      "trigger": "minecraft:location",
                      "conditions": {
                        "player": {
                          "type": "minecraft:entity_properties",
                          "entity": "this",
                          "predicate": { "minecraft:location": { "position": { "y": { "min": 100 } } } }
                        }
                      }
                    }
                  }
                }
                """);
        TEMPLATES.put("predicate", """
                {
                  "type": "minecraft:time_check",
                  "clock": "minecraft:overworld",
                  "value": { "min": 13000, "max": 23000 },
                  "period": 24000
                }
                """);
        TEMPLATES.put("item_modifier", """
                {
                  "type": "minecraft:set_name",
                  "entity": "this",
                  "name": { "text": "Legendary Sword", "color": "gold", "italic": false },
                  "target": "item_name"
                }
                """);
        TEMPLATES.put("dialog", """
                {
                  "type": "minecraft:multi_action",
                  "title": { "text": "Village Elder" },
                  "body": { "type": "minecraft:plain_message", "contents": { "text": "Welcome, traveller. What do you seek?" } },
                  "actions": [
                    { "label": { "text": "A quest" }, "action": { "type": "minecraft:run_command", "command": "trigger mk_dialog set 1" } },
                    { "label": { "text": "Nothing" }, "action": { "type": "minecraft:run_command", "command": "trigger mk_dialog set 2" } }
                  ],
                  "columns": 2,
                  "can_close_with_escape": true
                }
                """);
        TEMPLATES.put("enchantment", """
                {
                  "description": { "text": "Lifesteal" },
                  "supported_items": "#minecraft:enchantable/sharp_weapon",
                  "weight": 2,
                  "max_level": 3,
                  "min_cost": { "base": 10, "per_level_above_first": 10 },
                  "max_cost": { "base": 40, "per_level_above_first": 10 },
                  "anvil_cost": 4,
                  "slots": [ "mainhand" ],
                  "effects": {}
                }
                """);
        TEMPLATES.put("damage_type", """
                {
                  "message_id": "trap",
                  "exhaustion": 0.1,
                  "scaling": "when_caused_by_living_non_player"
                }
                """);
        TEMPLATES.put("tags/function", "{\n  \"values\": [\n    \"my_map:my_function\"\n  ]\n}\n");
        TEMPLATES.put("tags/item", "{\n  \"values\": [\n    \"minecraft:diamond\",\n    \"minecraft:emerald\"\n  ]\n}\n");
        TEMPLATES.put("tags/block", "{\n  \"values\": [\n    \"minecraft:stone\"\n  ]\n}\n");
        TEMPLATES.put("tags/entity_type", "{\n  \"values\": [\n    \"minecraft:zombie\"\n  ]\n}\n");
        TEMPLATES.put("jukebox_song", """
                {
                  "sound_event": "minecraft:music_disc.cat",
                  "description": { "text": "My Song" },
                  "length_in_seconds": 185,
                  "comparator_output": 3
                }
                """);
        TEMPLATES.put("painting_variant", """
                {
                  "asset_id": "my_map:my_painting",
                  "width": 2,
                  "height": 2,
                  "title": { "text": "My Painting" }
                }
                """);
        TEMPLATES.put("trim_material", """
                {
                  "description": { "text": "Ruby", "color": "#E0115F" },
                  "palette_id": "my_map:trim/ruby"
                }
                """);
        TEMPLATES.put("banner_pattern", "{\n  \"asset_id\": \"my_map:emblem\",\n  \"translation_key\": \"block.my_map.banner.emblem\"\n}\n");
        TEMPLATES.put("chat_type", """
                {
                  "chat": { "translation_key": "chat.type.text", "parameters": [ "sender", "content" ] },
                  "narration": { "translation_key": "chat.type.text.narrate", "parameters": [ "sender", "content" ] }
                }
                """);
    }

    private JsonTemplates() {}

    /** File types for the "new file" dialog: function, tags, then every registry folder the server reported. */
    public static List<String> types(List<String> registryFolders) {
        List<String> out = new ArrayList<>();
        out.add("function");
        for (String k : TEMPLATES.keySet()) if (!out.contains(k)) out.add(k);
        for (String r : registryFolders) if (!out.contains(r)) out.add(r);
        for (String r : List.of("tags/function", "tags/item", "tags/block", "tags/entity_type")) if (!out.contains(r)) out.add(r);
        return out;
    }

    public static String template(String type) {
        return TEMPLATES.getOrDefault(type, "{\n}\n");
    }

    /** data/&lt;ns&gt;/&lt;type&gt;/&lt;name&gt;.json (or .mcfunction). */
    public static String path(String ns, String type, String name) {
        String ext = type.equals("function") ? ".mcfunction" : ".json";
        return "data/" + ns + "/" + type + "/" + BlockCompiler.sanitizePath(name) + ext;
    }
}
