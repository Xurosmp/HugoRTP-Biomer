package de.hugo.rtp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class HugoRtpScreen extends Screen {
    private final Screen parent;
    private final HugoRtpConfig cfg = HugoRtpMod.config;

    public HugoRtpScreen(Screen parent) {
        super(Text.translatable("hugortp.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 155;
        int right = width / 2 + 5;
        int w = 150;
        int y = 50;
        int step = 24;

        TextFieldWidget biomes = new TextFieldWidget(textRenderer, left, y, 310, 20, Text.translatable("hugortp.biomes"));
        biomes.setMaxLength(200);
        biomes.setText(cfg.biomes);
        biomes.setChangedListener(s -> cfg.biomes = s);
        addDrawableChild(biomes);

        y += step + 10;
        TextFieldWidget command = new TextFieldWidget(textRenderer, left, y, 310, 20, Text.translatable("hugortp.command"));
        command.setMaxLength(100);
        command.setText(cfg.command);
        command.setChangedListener(s -> cfg.command = s);
        addDrawableChild(command);

        y += step + 4;
        addDrawableChild(new IntSlider(left, y, w, 1, 30, clamp(cfg.cooldownSeconds, 1, 30),
                v -> cfg.cooldownSeconds = v,
                v -> Text.translatable("hugortp.cooldown", v)));
        addDrawableChild(new IntSlider(right, y, w, 1, 10, clamp(cfg.settleSeconds, 1, 10),
                v -> cfg.settleSeconds = v,
                v -> Text.translatable("hugortp.settle", v)));

        y += step;
        addDrawableChild(new IntSlider(left, y, w, 5, 60, clamp(cfg.timeoutSeconds, 5, 60),
                v -> cfg.timeoutSeconds = v,
                v -> Text.translatable("hugortp.timeout", v)));
        addDrawableChild(new IntSlider(right, y, w, 0, 500, clamp(cfg.maxAttempts, 0, 500),
                v -> cfg.maxAttempts = v,
                v -> Text.translatable("hugortp.maxattempts",
                        v == 0 ? Text.translatable("hugortp.unlimited").getString() : String.valueOf(v))));

        y += step;
        toggle(left, y, w, () -> Text.translatable("hugortp.hud").append(": ")
                        .append(cfg.hudEnabled ? ScreenTexts.ON : ScreenTexts.OFF),
                () -> cfg.hudEnabled = !cfg.hudEnabled);

        addDrawableChild(ButtonWidget.builder(startStopText(), b -> {
            HugoRtpMod.instance.toggle(MinecraftClient.getInstance());
            b.setMessage(startStopText());
        }).dimensions(right, y, w, 20).build());

        y += step;
        addDrawableChild(new IntSlider(left, y, w, 0, Math.max(1, width - 20), clamp(cfg.hudX, 0, Math.max(1, width - 20)),
                v -> cfg.hudX = v,
                v -> Text.translatable("hugortp.hudx", v)));
        addDrawableChild(new IntSlider(right, y, w, 0, Math.max(1, height - 20), clamp(cfg.hudY, 0, Math.max(1, height - 20)),
                v -> cfg.hudY = v,
                v -> Text.translatable("hugortp.hudy", v)));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
                .dimensions(width / 2 - 100, height - 30, 200, 20).build());
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Text startStopText() {
        return Text.translatable(HugoRtpMod.instance.isRunning() ? "hugortp.stop" : "hugortp.start");
    }

    private void toggle(int x, int y, int w, Supplier<Text> label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(label.get(), b -> {
            action.run();
            b.setMessage(label.get());
        }).dimensions(x, y, w, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("hugortp.biomes"), width / 2 - 155, 38, 0xFFA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.translatable("hugortp.command"), width / 2 - 155, 72, 0xFFA0A0A0);
    }

    @Override
    public void close() {
        cfg.save();
        client.setScreen(parent);
    }

    /** Slider fuer ganze Zahlen. */
    private static class IntSlider extends SliderWidget {
        private final int min;
        private final int max;
        private final Consumer<Integer> onChange;
        private final Function<Integer, Text> format;

        IntSlider(int x, int y, int w, int min, int max, int value,
                  Consumer<Integer> onChange, Function<Integer, Text> format) {
            super(x, y, w, 20, Text.empty(), (value - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            this.format = format;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            if (format != null) {
                setMessage(format.apply(current()));
            }
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
