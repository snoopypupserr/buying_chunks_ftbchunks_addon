package snoopypupser.buyingchunks.mixin;

import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snoopypupser.buyingchunks.client.SellModeState;

@Mixin(ChunkScreen.class)
public class ChunkScreenMixin {

    @Unique
    private SimpleButton buyingchunks$sellToggleButton;

    @Unique
    private SimpleButton buyingchunks$sellStatusLabel;

    @Inject(method = "addWidgets", at = @At("TAIL"), remap = false)
    private void buyingchunks$addSellButton(CallbackInfo ci) {
        SellModeState.setSellMode(false);
        ChunkScreen self = (ChunkScreen) (Object) this;

        buyingchunks$sellToggleButton = new SimpleButton(self,
                Component.literal(""),
                Icons.ACCEPT,
                (btn, mb) -> {
                    boolean newMode = !SellModeState.isSellMode();
                    SellModeState.setSellMode(newMode);
                }) {
            @Override
            public void addMouseOverText(TooltipList list) {
                boolean mode = SellModeState.isSellMode();
                if (mode) {
                    list.add(Component.translatable("uc7core.claimshop.bulksell.mode_on_tip1"));
                    list.add(Component.translatable("uc7core.claimshop.bulksell.mode_on_tip2"));
                } else {
                    list.add(Component.translatable("uc7core.claimshop.bulksell.mode_off_tip1"));
                    list.add(Component.translatable("uc7core.claimshop.bulksell.mode_off_tip2"));
                }
            }
        };
        buyingchunks$sellToggleButton.setForceButtonSize(false);
        self.add(buyingchunks$sellToggleButton);

        buyingchunks$sellStatusLabel = new SimpleButton(self,
                Component.literal(""),
                Icons.ACCEPT,
                (btn, mb) -> {
                    boolean newMode = !SellModeState.isSellMode();
                    SellModeState.setSellMode(newMode);
                }) {
            @Override
            public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
                Component text = SellModeState.isSellMode()
                        ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                        : Component.literal("OFF").withStyle(ChatFormatting.RED);
                theme.drawString(graphics, text, x, y + 2);
            }
        };
        buyingchunks$sellStatusLabel.setForceButtonSize(false);
        self.add(buyingchunks$sellStatusLabel);
    }

    @Inject(method = "alignWidgets", at = @At("TAIL"), remap = false)
    private void buyingchunks$alignSellButton(CallbackInfo ci) {
        if (buyingchunks$sellToggleButton != null) {
            buyingchunks$sellToggleButton.setPosAndSize(20, 2, 12, 12);
        }
        if (buyingchunks$sellStatusLabel != null) {
            buyingchunks$sellStatusLabel.setPosAndSize(34, 2, 24, 12);
        }
    }

    @Inject(method = "doCancel", at = @At("HEAD"), remap = false)
    private void buyingchunks$onClose(CallbackInfo ci) {
        SellModeState.setSellMode(false);
    }
}
