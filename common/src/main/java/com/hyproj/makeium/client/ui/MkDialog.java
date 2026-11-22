package com.hyproj.makeium.client.ui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Centered modal dialogs: a text prompt and a yes/no confirmation. */
public final class MkDialog {
    private MkDialog() {}

    public static void prompt(MkScreen screen, Component title, Component hint, String initial, Consumer<String> ok) {
        screen.openPopup(new Prompt(screen, title, hint, initial, ok));
    }

    public static void confirm(MkScreen screen, Component title, Component message, Runnable yes) {
        screen.openPopup(new Confirm(screen, title, message, yes));
    }

    private static final class Prompt extends MkWidgetPopup {
        private final Component title;
        private final MkField field;
        private final Consumer<String> ok;
        private final MkButton okButton, cancel;

        Prompt(MkScreen s, Component title, Component hint, String initial, Consumer<String> ok) {
            super((s.width - 280) / 2, (s.height - 96) / 2, 280, 96);
            this.title = title;
            this.ok = ok;
            field = child(new MkField(0, 0, w - 24, 20, hint));
            field.value(initial);
            okButton = child(new MkButton(0, 0, 80, 20, Component.translatable("makeium.ui.ok"), MkButton.Kind.PRIMARY, this::submit));
            cancel = child(new MkButton(0, 0, 80, 20, Component.translatable("makeium.ui.cancel"), MkButton.Kind.DEFAULT, this::close));
            focus(field);
        }

        @Override
        protected void layout() {
            field.moveBox(x + 12, y + 32);
            okButton.setPosition(x + w - 92, y + h - 30);
            cancel.setPosition(x + w - 178, y + h - 30);
        }

        private void submit() {
            close();
            ok.accept(field.getValue());
        }

        @Override
        protected boolean onEnter() {
            submit();
            return true;
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
            if (e.isConfirmation()) {
                submit();
                return true;
            }
            return super.keyPressed(e);
        }

        @Override
        public boolean closeOnOutsideClick() {
            return false;
        }

        @Override
        public boolean dims() {
            return true;
        }

        @Override
        protected void renderFrame(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            MkTheme.modal(g, x, y, w, h);
            MkTheme.boldText(g, title, x + 12, y + 12, MkTheme.TEXT);
        }
    }

    private static final class Confirm extends MkWidgetPopup {
        private final Component title, message;
        private final MkButton yesButton, noButton;

        Confirm(MkScreen s, Component title, Component message, Runnable yes) {
            super((s.width - 300) / 2, (s.height - 110) / 2, 300, 40 + MkTheme.paragraphHeight(message, 276) + 36);
            this.title = title;
            this.message = message;
            yesButton = child(new MkButton(0, 0, 90, 20, Component.translatable("makeium.ui.confirm"), MkButton.Kind.DANGER, () -> {
                close();
                yes.run();
            }));
            noButton = child(new MkButton(0, 0, 80, 20, Component.translatable("makeium.ui.cancel"), MkButton.Kind.DEFAULT, this::close));
        }

        @Override
        protected void layout() {
            yesButton.setPosition(x + w - 102, y + h - 30);
            noButton.setPosition(x + w - 188, y + h - 30);
        }

        @Override
        public boolean closeOnOutsideClick() {
            return false;
        }

        @Override
        public boolean dims() {
            return true;
        }

        @Override
        protected void renderFrame(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            MkTheme.modal(g, x, y, w, h);
            MkTheme.boldText(g, title, x + 12, y + 12, MkTheme.TEXT);
            MkTheme.paragraph(g, message, x + 12, y + 30, w - 24, MkTheme.MUTED);
        }
    }
}
