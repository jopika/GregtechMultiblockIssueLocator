package io.github.jopika.gtmultiblocklocator.mixin;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import io.github.jopika.gtmultiblocklocator.client.ClientHighlight;
import io.github.jopika.gtmultiblocklocator.client.ClientConfig;
import io.github.jopika.gtmultiblocklocator.client.ErrorLinks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/** Targets the pinned LDLib 1.0.52.a client widget; no server click action. */
@Mixin(value = ComponentPanelWidget.class, remap = false)
public abstract class ComponentPanelWidgetMixin extends Widget {
    @Shadow protected List<Component> lastText;
    @Shadow protected abstract Style getStyleUnderMouse(double mouseX, double mouseY);
    @Shadow public abstract void formatDisplayText();
    @Shadow public abstract void updateComponentTextSize();
    @Unique private ModularUI gtml$formattedGui;
    @Unique private boolean gtml$highlightEnabled;
    @Unique private Map<String, BlockPos> gtml$targets = Map.of();

    protected ComponentPanelWidgetMixin() { super(0, 0, 0, 0); }

    @Unique
    private boolean gtml$isController() {
        var mc = Minecraft.getInstance();
        return ClientConfig.HIGHLIGHT_ENABLED.get() && getGui() != null
                && getGui().holder instanceof MultiblockControllerMachine controller
                && mc.level != null && controller.getLevel() == mc.level;
    }

    @Redirect(method = "formatDisplayText()V", at = @At(value = "FIELD",
            target = "Lcom/lowdragmc/lowdraglib/gui/widget/ComponentPanelWidget;lastText:Ljava/util/List;",
            opcode = 180), require = 1)
    private List<Component> gtml$displayCopies(ComponentPanelWidget panel) {
        gtml$formattedGui = getGui();
        gtml$highlightEnabled = ClientConfig.HIGHLIGHT_ENABLED.get();
        gtml$targets = Map.of();
        if (!gtml$isController()) return lastText;
        var display = ErrorLinks.decorate(lastText);
        gtml$targets = display.targets();
        return display.lines();
    }

    @Inject(method = "updateScreen()V", at = @At("TAIL"))
    private void gtml$attached(CallbackInfo ci) {
        // Constructors format before setGui; recover once the holder is attached.
        if (getGui() != gtml$formattedGui || gtml$highlightEnabled != ClientConfig.HIGHLIGHT_ENABLED.get()) {
            formatDisplayText();
            updateComponentTextSize();
        }
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void gtml$click(double mouseX, double mouseY, int button,
                           CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !gtml$isController()) return;
        Style style = getStyleUnderMouse(mouseX, mouseY);
        ClickEvent click = style == null ? null : style.getClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND) return;
        BlockPos pos = gtml$targets.get(click.getValue());
        var level = Minecraft.getInstance().level;
        if (pos == null || level == null) return;
        // Consume our marker even if the displayed position is outside build height.
        if (pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()) {
            ClientHighlight.select(level, pos);
            playButtonClickSound();
        }
        cir.setReturnValue(true);
    }
}
