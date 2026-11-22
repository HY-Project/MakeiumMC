package com.hyproj.makeium.feature.datapack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Compiles a {@link Project}'s scripts into vanilla datapack files (26.3 layout: data/&lt;ns&gt;/function/...,
 * data/minecraft/tags/function/load.json, ...).
 *
 * <ul>
 *   <li>Each event script becomes a function; its hook goes into load/tick tags, an advancement, or a shared
 *       per-tick dispatcher ({@code <ns>:mk/tick}).</li>
 *   <li>A C block with one compiled line inlines it ({@code execute ... run <line>}); with more it calls a generated
 *       sub-function under {@code <ns>:mk/gen/}.</li>
 *   <li>if/else, repeat, while, chance and wait compile to sub-function patterns (see the methods below).</li>
 * </ul>
 */
public final class BlockCompiler {
    private static final Pattern SLOT = Pattern.compile("\\{([a-z_]+)}");
    private static final Pattern OPTIONAL = Pattern.compile("\\[\\[(.*?)]]");

    private final Project project;
    private final String ns;
    private final Map<String, String> out = new LinkedHashMap<>();
    private final List<String> loadTag = new ArrayList<>();
    private final List<String> tickTag = new ArrayList<>();
    private final List<String> setup = new ArrayList<>();
    private final List<String> tickLines = new ArrayList<>();
    private final List<String> tickCleanup = new ArrayList<>();
    private final Map<String, List<String>> functionTags = new LinkedHashMap<>();
    private final Set<String> triggerObjectives = new LinkedHashSet<>();
    private final List<String> problems = new ArrayList<>();
    private int genCounter;
    private String currentScript = "";

    public record Result(Map<String, String> files, List<String> problems) {}

    private BlockCompiler(Project project) {
        this.project = project;
        this.ns = sanitize(project.namespace.isBlank() ? "my_map" : project.namespace);
    }

    public static Result compile(Project project) {
        BlockCompiler c = new BlockCompiler(project);
        c.run();
        return new Result(c.out, c.problems);
    }

    /** Compiles just one script's main function (for the live code preview). */
    public static String preview(Project project, Script script) {
        BlockCompiler c = new BlockCompiler(project);
        Node head = script.head();
        if (head == null) return "";
        BlockDef d = BlockDefs.get(head.def);
        if (d.shape() != BlockDef.Shape.HAT) {
            List<String> lines = c.lines(script.nodes, "preview");
            return String.join("\n", lines);
        }
        String fn = c.compileScript(script, 0);
        String main = c.out.get(c.path(fn));
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(c.ns).append(':').append(fn).append('\n');
        if (main != null) sb.append(main);
        for (Map.Entry<String, String> e : c.out.entrySet()) {
            if (e.getKey().equals(c.path(fn))) continue;
            if (!e.getKey().endsWith(".mcfunction")) continue;
            sb.append("\n# ").append(e.getKey().replace("data/" + c.ns + "/function/", c.ns + ":").replace(".mcfunction", "")).append('\n').append(e.getValue());
        }
        return sb.toString();
    }

    private void run() {
        int index = 0;
        for (Script s : project.scripts) {
            Node head = s.head();
            if (head == null || BlockDefs.get(head.def).shape() != BlockDef.Shape.HAT || head.disabled) continue;
            compileScript(s, index++);
        }
        // shared load: objectives the hooks need
        if (!setup.isEmpty() || !triggerObjectives.isEmpty()) {
            List<String> load = new ArrayList<>();
            load.add("# Makeium: objectives and setup for " + ns);
            load.addAll(new LinkedHashSet<>(setup));
            write("mk/load", load);
            loadTag.add(0, ns + ":mk/load");
        }
        if (!tickLines.isEmpty() || !triggerObjectives.isEmpty()) {
            List<String> tick = new ArrayList<>();
            tick.add("# Makeium: per-tick event dispatch for " + ns);
            tick.addAll(tickLines);
            tick.addAll(new LinkedHashSet<>(tickCleanup));
            for (String obj : triggerObjectives) tick.add("scoreboard players enable @a " + obj);
            write("mk/tick", tick);
            tickTag.add(ns + ":mk/tick");
        }
        if (!loadTag.isEmpty()) out.put("data/minecraft/tags/function/load.json", tag(loadTag));
        if (!tickTag.isEmpty()) out.put("data/minecraft/tags/function/tick.json", tag(tickTag));
        for (Map.Entry<String, List<String>> e : functionTags.entrySet()) out.put(e.getKey(), tag(e.getValue()));
    }

    /** Returns the function path (without namespace) of the script's main function. */
    private String compileScript(Script s, int index) {
        Node head = s.head();
        String ev = head.def;
        currentScript = ev.replace("ev_", "") + "_" + index;
        String fn = "mk/event/" + currentScript;
        List<Node> body = s.nodes.subList(1, s.nodes.size());
        List<String> lines = new ArrayList<>();
        if (!head.comment.isEmpty()) lines.add("# " + head.comment);
        switch (ev) {
            case "ev_load" -> loadTag.add(ns + ":" + fn);
            case "ev_tick" -> tickTag.add(ns + ":" + fn);
            case "ev_every" -> {
                int t = Math.max(1, parseInt(head.value("ticks", "20"), 20));
                lines.add("schedule function " + ns + ":" + fn + " " + t + "t replace");
                loadTag.add(ns + ":" + fn);
            }
            case "ev_function" -> {
                String name = head.value("name", "my_function").strip();
                if (name.contains(":")) {
                    String[] p = name.split(":", 2);
                    if (!sanitize(p[0]).equals(ns)) {
                        // a function in another namespace: write it there
                        String full = sanitizePath(p[1]);
                        List<String> l = lines(body, full);
                        out.put("data/" + sanitize(p[0]) + "/function/" + full + ".mcfunction", join(l));
                        return full;
                    }
                    name = p[1];
                }
                fn = sanitizePath(name);
            }
            case "ev_join" -> {
                setup.add("scoreboard objectives add " + obj("leave") + " minecraft.custom:minecraft.leave_game");
                tickLines.add("tag @a[scores={" + obj("leave") + "=1..}] add " + ns + "_join");
                tickLines.add("tag @a[tag=!" + ns + "_seen] add " + ns + "_join");
                tickLines.add("tag @a[tag=" + ns + "_join] add " + ns + "_seen");
                tickLines.add("execute as @a[tag=" + ns + "_join] at @s run function " + ns + ":" + fn);
                tickCleanup.add("tag @a remove " + ns + "_join");
                tickCleanup.add("scoreboard players reset @a " + obj("leave"));
            }
            case "ev_first_join" -> {
                tickLines.add("execute as @a[tag=!" + ns + "_first] at @s run function " + ns + ":" + fn);
                tickCleanup.add("tag @a[tag=!" + ns + "_first] add " + ns + "_first");
            }
            case "ev_death" -> {
                setup.add("scoreboard objectives add " + obj("deaths") + " deathCount");
                tickLines.add("execute as @a[scores={" + obj("deaths") + "=1..}] at @s run function " + ns + ":" + fn);
                tickCleanup.add("scoreboard players reset @a[scores={" + obj("deaths") + "=1..}] " + obj("deaths"));
            }
            case "ev_trigger" -> {
                String o = head.value("objective", "menu").strip();
                setup.add("scoreboard objectives add " + o + " trigger");
                triggerObjectives.add(o);
                tickLines.add("execute as @a[scores={" + o + "=1..}] at @s run function " + ns + ":" + fn);
                tickCleanup.add("scoreboard players reset @a[scores={" + o + "=1..}] " + o);
            }
            case "ev_interact" -> {
                String tag = head.value("tag", "my_button").strip();
                advancement(fn, "minecraft:player_interacted_with_entity", Predicates.interactedWithTagged(tag));
                lines.add("advancement revoke @s only " + ns + ":" + fn);
            }
            case "ev_advancement" -> {
                JsonObject cond;
                try {
                    String raw = head.value("conditions", "{}").strip();
                    cond = raw.isEmpty() ? new JsonObject() : JsonParser.parseString(raw).getAsJsonObject();
                } catch (RuntimeException e) {
                    problems.add(currentScript + ": conditions are not valid JSON");
                    cond = new JsonObject();
                }
                advancement(fn, head.value("trigger", "minecraft:location"), cond);
                lines.add("advancement revoke @s only " + ns + ":" + fn);
            }
            case "ev_region" -> {
                String region = sanitizePath(head.value("region", "spawn")).replace('/', '_');
                String mode = head.value("mode", "enter");
                addToFunctionTag("makeium", "region/" + region + "/" + mode, ns + ":" + fn);
            }
            case "ev_dialog" -> addToFunctionTag("makeium", "dialog/" + parseInt(head.value("choice", "1"), 1), ns + ":" + fn);
            default -> problems.add("Unknown event " + ev);
        }
        lines.addAll(lines(body, fn));
        write(fn, lines);
        return fn;
    }

    private void advancement(String fn, String trigger, JsonObject conditions) {
        JsonObject adv = new JsonObject();
        JsonObject criteria = new JsonObject();
        JsonObject c = new JsonObject();
        c.addProperty("trigger", trigger);
        if (conditions.size() > 0) c.add("conditions", conditions);
        criteria.add("event", c);
        adv.add("criteria", criteria);
        JsonObject rewards = new JsonObject();
        rewards.addProperty("function", ns + ":" + fn);
        adv.add("rewards", rewards);
        out.put("data/" + ns + "/advancement/" + fn + ".json", adv.toString());
    }

    private void addToFunctionTag(String tagNs, String path, String fn) {
        functionTags.computeIfAbsent("data/" + tagNs + "/tags/function/" + path + ".json", k -> new ArrayList<>()).add(fn);
    }

    private String obj(String name) {
        String o = ns + "_" + name;
        return o.length() > 32 ? o.substring(0, 32) : o;
    }

    // ---------------------------------------------------------------- statements

    /** Compiles a list of nodes into function lines. {@code owner} names the function (for generated names). */
    List<String> lines(List<Node> nodes, String owner) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (n.disabled) continue;
            BlockDef d = BlockDefs.get(n.def);
            if (!n.comment.isEmpty()) lines.add("# " + n.comment.replace("\n", " "));
            switch (n.def) {
                case "comment" -> lines.add("# " + n.value("text", "").replace("\n", " "));
                case "wait" -> {
                    // the rest of this list continues later, from a scheduled function (runs as the server)
                    int t = Math.max(1, parseInt(n.value("ticks", "20"), 20));
                    String cont = gen(nodes.subList(i + 1, nodes.size()), owner);
                    lines.add("schedule function " + ns + ":" + cont + " " + t + "t append");
                    return lines;
                }
                case "if_else" -> {
                    String cond = n.value("cond", "").strip();
                    String then = gen(n.body, owner), otherwise = gen(n.elseBody, owner);
                    String wrapper = genLines(List.of(
                            "execute if " + cond + " run return run function " + ns + ":" + then,
                            "function " + ns + ":" + otherwise), owner);
                    lines.add("function " + ns + ":" + wrapper);
                }
                case "repeat" -> {
                    int times = Math.max(0, parseInt(n.value("times", "3"), 3));
                    String counter = "#" + owner.replace('/', '_') + "_r" + (genCounter + 1);
                    setup.add("scoreboard objectives add " + obj("tmp") + " dummy");
                    String loop = nextName(owner);
                    List<String> loopLines = new ArrayList<>(lines(n.body, loop));
                    loopLines.add("scoreboard players remove " + counter + " " + obj("tmp") + " 1");
                    loopLines.add("execute if score " + counter + " " + obj("tmp") + " matches 1.. run function " + ns + ":" + loop);
                    write(loop, loopLines);
                    lines.add("scoreboard players set " + counter + " " + obj("tmp") + " " + times);
                    lines.add("execute if score " + counter + " " + obj("tmp") + " matches 1.. run function " + ns + ":" + loop);
                }
                case "while" -> {
                    String cond = n.value("cond", "").strip();
                    String loop = nextName(owner);
                    String bodyFn = nextName(owner);
                    List<String> bodyLines = new ArrayList<>(lines(n.body, bodyFn));
                    bodyLines.add("function " + ns + ":" + loop);
                    write(bodyFn, bodyLines);
                    write(loop, List.of("execute if " + cond + " run function " + ns + ":" + bodyFn));
                    lines.add("function " + ns + ":" + loop);
                }
                case "chance" -> {
                    double p = parseDouble(n.value("chance", "0.5"), 0.5);
                    p = Math.max(0, Math.min(1, p));
                    lines.add(runBody("execute if predicate {type:\"minecraft:random_chance\",chance:" + trim(p) + "} run ", n.body, owner));
                }
                default -> {
                    if (d.shape() == BlockDef.Shape.C) {
                        String tpl = fill(d, n);
                        int at = tpl.indexOf("{body}");
                        String prefix = at < 0 ? tpl + " " : tpl.substring(0, at);
                        lines.add(runBody(prefix, n.body, owner));
                    } else if (d.shape() == BlockDef.Shape.STATEMENT) {
                        String cmd = fill(d, n).strip();
                        if (cmd.startsWith("/")) cmd = cmd.substring(1);
                        if (!cmd.isEmpty()) lines.add(cmd);
                    }
                }
            }
        }
        return lines;
    }

    /** "execute ... run " + the body: inline when it is one plain command, else a generated function. */
    private String runBody(String prefix, List<Node> body, String owner) {
        List<String> inner = lines(body, owner);
        List<String> commands = inner.stream().filter(l -> !l.startsWith("#")).toList();
        if (commands.size() == 1 && !commands.get(0).startsWith("$") && inner.size() == 1) return prefix + commands.get(0);
        String fn = nextName(owner);
        write(fn, inner);
        return prefix + "function " + ns + ":" + fn;
    }

    private String gen(List<Node> nodes, String owner) {
        String fn = nextName(owner);
        write(fn, lines(nodes, fn));
        return fn;
    }

    private String genLines(List<String> lines, String owner) {
        String fn = nextName(owner);
        write(fn, lines);
        return fn;
    }

    private String nextName(String owner) {
        genCounter++;
        String base = owner.startsWith("mk/") ? owner.substring(3) : owner;
        return "mk/gen/" + base.replace('/', '_') + "_" + genCounter;
    }

    /** Fills a template with the node's slot values; drops optional [[...]] parts whose slots are blank. */
    static String fill(BlockDef d, Node n) {
        String tpl = d.template();
        Matcher opt = OPTIONAL.matcher(tpl);
        StringBuilder sb = new StringBuilder();
        while (opt.find()) {
            String part = opt.group(1);
            Matcher m = SLOT.matcher(part);
            boolean keep = true;
            while (m.find()) {
                if (m.group(1).equals("body")) continue;
                if (n.value(m.group(1), "").isBlank()) keep = false;
            }
            opt.appendReplacement(sb, Matcher.quoteReplacement(keep ? part : ""));
        }
        opt.appendTail(sb);
        Matcher m = SLOT.matcher(sb.toString());
        StringBuilder res = new StringBuilder();
        while (m.find()) {
            String slot = m.group(1);
            String v = slot.equals("body") ? "{body}" : n.value(slot, d.slot(slot) == null ? "" : d.slot(slot).def()).strip();
            m.appendReplacement(res, Matcher.quoteReplacement(v));
        }
        m.appendTail(res);
        return res.toString();
    }

    private void write(String fn, List<String> lines) {
        out.put(path(fn), join(lines));
    }

    private String path(String fn) {
        return "data/" + ns + "/function/" + fn + ".mcfunction";
    }

    private static String join(List<String> lines) {
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }

    private static String tag(List<String> values) {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        for (String v : new LinkedHashSet<>(values)) a.add(v);
        o.add("values", a);
        return o.toString();
    }

    public static String sanitize(String s) {
        String r = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-]", "_");
        return r.isEmpty() ? "my_map" : r;
    }

    public static String sanitizePath(String s) {
        String r = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-/]", "_").replaceAll("/+", "/");
        while (r.startsWith("/")) r = r.substring(1);
        while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r.isEmpty() ? "unnamed" : r;
    }

    private static int parseInt(String s, int def) {
        try {
            return (int) Math.round(Double.parseDouble(s.strip()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String trim(double v) {
        String s = Double.toString(v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
