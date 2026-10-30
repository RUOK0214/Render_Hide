package com.ruok0214.renderhide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ChangedBlockRevealScreen extends Screen {
    private final Screen parent;
    private boolean enabled = ChangedBlockReveal.enabled();
    private String value = Double.toString(ChangedBlockReveal.seconds());
    private EditBox seconds;
    private boolean invalid;
    public ChangedBlockRevealScreen(Screen parent) {
        super(Component.translatable("renderhide.changed.title")); this.parent = parent;
    }
    protected void init() {
        int w = Math.min(300, width - 20), x = (width - w) / 2, y = Math.max(28, height / 2 - 60);
        addRenderableWidget(Button.builder(toggleText(), b -> {
            enabled = !enabled; b.setMessage(toggleText());
        }).bounds(x, y, w, 20).build());
        seconds = new EditBox(font, x, y + 46, w, 20, Component.translatable("renderhide.changed.duration"));
        seconds.setMaxLength(8); seconds.setValue(value);
        seconds.setResponder(s -> {value = s; invalid = false;});
        addRenderableWidget(seconds);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            try {
                double duration = Double.parseDouble(seconds.getValue().trim());
                if (!Double.isFinite(duration) || duration < 0.1 || duration > 60) { invalid = true; return; }
                ChangedBlockReveal.configure(enabled, duration); onClose();
            } catch (NumberFormatException e) { invalid = true; }
        }).bounds(x, y + 100, (w - 8) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x + (w + 8) / 2, y + 100, (w - 8) / 2, 20).build());
    }
    private Component toggleText() {
        return Component.translatable("renderhide.changed.toggle", Component.translatable(enabled ? "options.on" : "options.off"));
    }
    public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int w = Math.min(300, width - 20), x = (width - w) / 2, y = Math.max(28, height / 2 - 60);
        context.fill(x - 6, y - 28, x + w + 6, y + 126, 0xE0202830);
        context.centeredText(font, title, width / 2, y - 20, 0xFFFFFF);
        context.text(font, Component.translatable("renderhide.changed.duration"), x, y + 30, 0xFFFFFF);
        context.text(font, Component.translatable(invalid ? "renderhide.changed.invalid" : "renderhide.changed.hint"), x, y + 76, invalid ? 0xFF7777 : 0xBBBBBB);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
