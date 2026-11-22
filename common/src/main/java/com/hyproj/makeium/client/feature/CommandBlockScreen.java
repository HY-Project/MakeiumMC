package com.hyproj.makeium.client.feature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkDropdown;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToggle;
import com.hyproj.makeium.mixin.client.CommandBlockEditScreenAccessor;
import com.hyproj.makeium.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A roomier command block editor: multi-line editing (lines are joined with spaces when saved, so long commands stay
 * readable), live validation and suggestions from the server's command tree, the chain this block belongs to, and
 * the usual mode / conditional / always-active / track-output switches. "Vanilla editor" opens the original screen;
 * the choice is remembered in config/makeium-client.properties.
 */
public class CommandBlockScreen extends MkScreen {
    private static Boolean enabled;
    private static boolean bypassOnce;

    private final CommandBlockEntity be;
    private CommandBlockEntity.Mode mode;
    private boolean conditional, automatic, track;
    private MultiLineEditBox box;
    private String loadedCommand;
    private boolean edited;
    private String error;
    private List<String> suggestions = List.of();
    private CompletableFuture<Suggestions> pending;

    private CommandBlockScreen(CommandBlockEntity be) {
        super(Component.translatable("makeium.cmdblock.title"), null);
        this.be = be;
        readBlock();
    }

    private static Path configFile() {
        return Platform.get().configDir().resolve("makeium-client.properties");
    }

    public static boolean enabled() {
        if (bypassOnce) {
            bypassOnce = false;
            return false;
        }
        if (enabled == null) {
            enabled = true;
            try {
                if (Files.exists(configFile()) && Files.readString(configFile()).contains("commandBlockEditor=false")) enabled = false;
            } catch (Exception ignored) {
                // default on
            }
        }
        return enabled;
    }

    static void setEnabled(boolean v) {
        enabled = v;
        try {
            Files.createDirectories(configFile().getParent());
            Files.writeString(configFile(), "commandBlockEditor=" + v + "\n");
        } catch (Exception ignored) {
            // not persisted
        }
    }

    public static Screen wrap(CommandBlockEditScreen s) {
        return new CommandBlockScreen(((CommandBlockEditScreenAccessor) s).makeium$commandBlock());
    }

    private void readBlock() {
        mode = be.getMode();
        conditional = be.isConditional();
        automatic = be.isAutomatic();
        track = be.getCommandBlock().isTrackOutput();
        loadedCommand = be.getCommandBlock().getCommand();
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70 + 76 + 110;
        int y = TOP + 22;
        int w = width - 2 * PAD;
        int boxH = Math.max(40, Math.min(110, height - y - 150));
        box = MultiLineEditBox.builder().setX(PAD + 4).setY(y + 4).setShowBackground(false).setShowDecorations(true)
                .setTextColor(MkTheme.TEXT).setTextShadow(false).setCursorColor(MkTheme.GREEN_HOVER)
                .setPlaceholder(Component.translatable("makeium.cmdblock.placeholder").withColor(MkTheme.FAINT))
                .build(MkTheme.mono(), w - 8, boxH - 8, Component.empty());
        box.setValue(loadedCommand, true);
        box.setValueListener(s -> {
            edited = true;
            validate();
        });
        add(box);
        setInitialFocus(box);
        int cy = y + boxH + 58;
        int cw = (w - 12) / 4;
        add(new MkDropdown<>(this, PAD, cy, cw, 18, List.of(CommandBlockEntity.Mode.values()), mode,
                m -> Component.translatable("advMode.mode." + m.name().toLowerCase(Locale.ROOT)), m -> mode = m));
        add(new MkToggle(PAD + cw + 4, cy + 1, cw, Component.translatable("advMode.mode.conditional"), conditional, v -> conditional = v));
        add(new MkToggle(PAD + 2 * (cw + 4), cy + 1, cw, Component.translatable("advMode.mode.autoexec.bat"), automatic, v -> automatic = v));
        add(new MkToggle(PAD + 3 * (cw + 4), cy + 1, cw, Component.translatable("advMode.trackOutput"), track, v -> track = v));
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.cancel"), MkButton.Kind.GHOST, this::onClose));
        add(new MkButton(width - PAD - 60 - 6 - 70, 4, 70, 20, Component.translatable("makeium.ui.done"), MkButton.Kind.PRIMARY, this::done));
        add(new MkButton(width - PAD - 60 - 6 - 70 - 6 - 104, 4, 104, 20, Component.translatable("makeium.cmdblock.vanilla"), MkButton.Kind.DEFAULT, () -> {
            bypassOnce = true;
            minecraft.player.openCommandBlock(be);
        }));
        add(new MkToggle(PAD, height - 20, 240, Component.translatable("makeium.cmdblock.use_makeium"), true, v -> setEnabled(v)));
        validate();
    }

    @Override
    public void tick() {
        super.tick();
        // the server sends the block's command right after the screen opens: pick it up until the user types
        if (!edited) {
            String now = be.getCommandBlock().getCommand();
            if (!now.equals(loadedCommand)) {
                readBlock();
                rebuildWidgets();
            }
        }
    }

    private String command() {
        return box.getValue().replace('\n', ' ').replaceAll(" {2,}", " ").strip();
    }

    private void validate() {
        error = null;
        suggestions = List.of();
        var conn = minecraft.getConnection();
        if (conn == null || box == null) return;
        String text = command();
        if (text.startsWith("/")) text = text.substring(1);
        if (text.isEmpty()) return;
        try {
            var dispatcher = conn.getCommands();
            ParseResults<ClientSuggestionProvider> parse = dispatcher.parse(text, conn.getSuggestionsProvider());
            CommandSyntaxException ex = Commands.getParseException(parse);
            if (ex != null) error = ex.getMessage();
            if (pending != null) pending.cancel(true);
            final String t = text;
            pending = dispatcher.getCompletionSuggestions(parse, t.length());
            pending.thenAccept(s -> minecraft.execute(() -> {
                List<String> l = new ArrayList<>();
                for (Suggestion sg : s.getList()) {
                    l.add(sg.getText());
                    if (l.size() >= 8) break;
                }
                suggestions = l;
            }));
        } catch (RuntimeException e) {
            error = e.getMessage();
        }
    }

    /** Dev runs: replace the command text and press Done. */
    public void devSetAndDone(String command) {
        box.setValue(command, true);
        done();
    }

    private void done() {
        minecraft.getConnection().send(new ServerboundSetCommandBlockPacket(be.getBlockPos(), command(), mode, track, conditional, automatic));
        onClose();
    }

    @Override
    public void onClose() {
        if (pending != null) pending.cancel(true);
        minecraft.gui.setScreen(null);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_TAB && !suggestions.isEmpty() && box != null && box.isFocused()) {
            String v = box.getValue();
            int cut = Math.max(v.lastIndexOf(' '), v.lastIndexOf('\n')) + 1;
            box.setValue(v.substring(0, cut) + suggestions.get(0), true);
            return true;
        }
        if (e.isConfirmation() && e.hasControlDown()) {
            done();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int y = TOP + 22;
        BlockPos p = be.getBlockPos();
        MkTheme.caption(g, Component.translatable("makeium.cmdblock.at", p.toShortString()), PAD, TOP + 8);
        if (box != null) {
            g.fill(PAD, y, width - PAD, y + box.getHeight() + 8, MkTheme.INPUT);
            MkTheme.border(g, PAD, y, width - 2 * PAD, box.getHeight() + 8, error == null ? (box.isFocused() ? MkTheme.GREEN_HOVER : MkTheme.LINE) : MkTheme.RED);
            int vy = y + box.getHeight() + 12;
            if (error != null) MkTheme.text(g, MkTheme.clip(MkTheme.font(), Component.literal(error), width - 2 * PAD), PAD, vy, MkTheme.RED);
            else if (!command().isEmpty()) MkTheme.text(g, Component.translatable("makeium.slot.valid"), PAD, vy, MkTheme.GREEN_TEXT);
            int sx = PAD;
            for (String s : suggestions) {
                int sw = MkTheme.mono().width(s) + 8;
                if (sx + sw > width - PAD) break;
                g.fill(sx, vy + 12, sx + sw, vy + 25, MkTheme.SURFACE);
                MkTheme.monoText(g, s, sx + 4, vy + 15, MkTheme.GREEN_TEXT);
                sx += sw + 3;
            }
            Component last = be.getCommandBlock().getLastOutput();
            if (!last.getString().isEmpty()) {
                MkTheme.text(g, MkTheme.clip(MkTheme.font(), Component.translatable("advMode.previousOutput").append(": ").append(last), width / 2 - PAD), width / 2, vy, MkTheme.MUTED);
            }
            drawChain(g, y + box.getHeight() + 8 + 58 + 26);
        }
    }

    /** The chain this block feeds: follows each command block's facing up to 8 blocks. */
    private void drawChain(GuiGraphicsExtractor g, int y) {
        if (minecraft.level == null || y > height - 44) return;
        MkTheme.caption(g, Component.translatable("makeium.cmdblock.chain"), PAD, y);
        BlockPos pos = be.getBlockPos();
        int x = PAD;
        for (int i = 0; i < 8; i++) {
            BlockState st = minecraft.level.getBlockState(pos);
            if (!(st.getBlock() instanceof CommandBlock)) break;
            BlockEntity e = minecraft.level.getBlockEntity(pos);
            String cmd = e instanceof CommandBlockEntity cb ? cb.getCommandBlock().getCommand() : "";
            String label = (i == 0 ? "▶ " : "→ ") + (cmd.isEmpty() ? "…" : MkTheme.clip(MkTheme.mono(), cmd, 90));
            int w = MkTheme.mono().width(label) + 10;
            if (x + w > width - PAD) break;
            int color = e instanceof CommandBlockEntity cb ? switch (cb.getMode()) {
                case SEQUENCE -> 0xFF3C8E7E;
                case AUTO -> 0xFF6A4FA8;
                default -> 0xFFB5712C;
            } : MkTheme.SURFACE;
            g.fill(x, y + 14, x + w, y + 28, MkTheme.withAlpha(color, 0.6f));
            MkTheme.monoText(g, label, x + 5, y + 17, MkTheme.TEXT);
            x += w + 4;
            Direction facing = st.getValue(CommandBlock.FACING);
            pos = pos.relative(facing);
        }
    }
}
