package com.hyproj.makeium.client.datapack;

import java.util.ArrayList;
import java.util.List;

import com.hyproj.makeium.client.Replies;
import com.hyproj.makeium.client.feature.PackState;
import com.hyproj.makeium.client.feature.RegionScreen;
import com.hyproj.makeium.client.tool.Selection;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkField;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.feature.link.LinkPack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Connects a spot in the world to a function, three ways: a hidden command block wired to the button / lever /
 * pressure plate you clicked; an invisible click box over the block (right-click runs the function, no redstone);
 * or a trigger region around the block.
 */
public class LinkScreen extends MkScreen {
    private static CompoundTag state = new CompoundTag();
    private static String lastFunction = "";
    private final BlockPos pos;
    private final @Nullable Direction face;
    private MkField function;
    private boolean requested;

    public LinkScreen(Screen parent, BlockPos pos, @Nullable Direction face) {
        super(Component.translatable("makeium.link.title"), parent);
        this.pos = pos.immutable();
        this.face = face;
    }

    /** Where the hidden command block goes so the clicked redstone component powers it. */
    private BlockPos commandBlockPos() {
        if (minecraft.level == null) return pos.below();
        BlockState st = minecraft.level.getBlockState(pos);
        if (st.getBlock() instanceof ButtonBlock || st.getBlock() instanceof LeverBlock) {
            // the component strongly powers the block it is attached to; put the command block under that block
            Direction attached = switch (st.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)) {
                case CEILING -> Direction.UP;
                case FLOOR -> Direction.DOWN;
                default -> st.getValue(FaceAttachedHorizontalDirectionalBlock.FACING).getOpposite();
            };
            return pos.relative(attached).below();
        }
        if (st.getBlock() instanceof PressurePlateBlock || st.getBlock() instanceof WeightedPressurePlateBlock) {
            return pos.below(2);
        }
        return pos.below();
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70;
        if (!requested) {
            requested = true;
            PackState.load(LinkPack.PACK, s -> state = s);
        }
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        int w = Math.min(360, width - 2 * PAD);
        int y = TOP + 40;
        function = add(MkField.mono(PAD, y, w, 18, Component.literal("namespace:function")));
        function.value(lastFunction);
        function.onChange(s -> lastFunction = s.strip());
        int bx = PAD;
        // suggestions: the functions of the datapack factory project
        for (String fn : DatapackScreen.functionNames()) {
            int bw = MkTheme.font().width(fn) + 12;
            if (bx + bw > PAD + w) break;
            add(new MkButton(bx, y + 22, bw, 16, Component.literal(fn), MkButton.Kind.CHIP, () -> {
                function.value(fn);
                lastFunction = fn;
            }));
            bx += bw + 3;
        }
        y += 50;
        BlockPos cb = commandBlockPos();
        add(new MkButton(PAD, y, w, 22, Component.translatable("makeium.link.cmdblock", cb.toShortString()), MkButton.Kind.PRIMARY, () -> {
            if (!check()) return;
            PackState.runCommand("setblock " + cb.getX() + " " + cb.getY() + " " + cb.getZ() + " minecraft:command_block{Command:\"function " + lastFunction + "\"} replace");
            MkToast.ok(Component.translatable("makeium.link.done"));
            onClose();
        }));
        add(new MkButton(PAD, y + 50, w, 22, Component.translatable("makeium.link.interact"), MkButton.Kind.PRIMARY, this::interact));
        add(new MkButton(PAD, y + 100, w, 22, Component.translatable("makeium.link.region"), MkButton.Kind.DEFAULT, () -> {
            Selection.setA(pos);
            Selection.setB(pos.above());
            minecraft.gui.setScreen(new RegionScreen(null));
        }));
    }

    private boolean check() {
        if (lastFunction.isBlank() || !lastFunction.contains(":")) {
            MkToast.error(Component.translatable("makeium.link.need_function"));
            return false;
        }
        return true;
    }

    private void interact() {
        if (!check()) return;
        String id = "l" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        id = id.replace('-', 'm');
        ListTag links = state.getListOrEmpty("links").copy();
        ListTag kept = new ListTag();
        for (Tag t : links) if (t instanceof CompoundTag c && !c.getStringOr("id", "").equals(id)) kept.add(c);
        CompoundTag link = new CompoundTag();
        link.putString("id", id);
        link.putString("function", lastFunction);
        link.putIntArray("pos", new int[]{pos.getX(), pos.getY(), pos.getZ()});
        kept.add(link);
        state.put("links", kept);
        List<LinkPack.Link> list = new ArrayList<>();
        for (Tag t : kept) if (t instanceof CompoundTag c) list.add(new LinkPack.Link(c.getStringOr("id", ""), c.getStringOr("function", "")));
        PackState.save(LinkPack.PACK, "Makeium world links", LinkPack.compile(list), state);
        PackState.runCommand("kill @e[type=minecraft:interaction,tag=" + LinkPack.tag(id) + "]");
        PackState.runCommand(LinkPack.summon(id, pos.getX(), pos.getY(), pos.getZ()));
        MkToast.ok(Component.translatable("makeium.link.done"));
        onClose();
    }

    @Override
    public void removed() {
        Replies.off("dp/load");
        super.removed();
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int w = Math.min(360, width - 2 * PAD);
        String block = minecraft.level == null ? "" : minecraft.level.getBlockState(pos).getBlock().getName().getString();
        MkTheme.text(g, Component.translatable("makeium.link.target", block, pos.toShortString()), PAD, TOP + 10, MkTheme.TEXT);
        MkTheme.caption(g, Component.translatable("makeium.link.function"), PAD, TOP + 28);
        int y = TOP + 90;
        MkTheme.paragraph(g, Component.translatable("makeium.link.cmdblock.desc"), PAD, y + 25, w, MkTheme.FAINT);
        MkTheme.paragraph(g, Component.translatable("makeium.link.interact.desc"), PAD, y + 75, w, MkTheme.FAINT);
        MkTheme.paragraph(g, Component.translatable("makeium.link.region.desc"), PAD, y + 125, w, MkTheme.FAINT);
    }
}
