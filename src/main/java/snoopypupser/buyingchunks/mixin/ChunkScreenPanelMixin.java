package snoopypupser.buyingchunks.mixin;

import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snoopypupser.buyingchunks.client.BulkSellConfirmScreen;
import snoopypupser.buyingchunks.client.SellModeState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel", remap = false)
public class ChunkScreenPanelMixin {

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, remap = false)
    private void buyingchunks$onMouseReleased(MouseButton button, CallbackInfo ci) {
        if (!SellModeState.isSellMode()) return;

        Object self = this;

        try {
            var selectedField = self.getClass().getDeclaredField("selectedChunks");
            selectedField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<XZ> selectedChunks = (Set<XZ>) selectedField.get(self);

            if (!selectedChunks.isEmpty()) {
                List<net.minecraft.world.level.ChunkPos> positions = new ArrayList<>();
                for (XZ xz : selectedChunks) {
                    positions.add(new net.minecraft.world.level.ChunkPos(xz.x(), xz.z()));
                }
                selectedChunks.clear();
                ci.cancel();

                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                }

                BulkSellConfirmScreen confirmScreen = new BulkSellConfirmScreen(positions, () -> {
                    ChunkScreen.openChunkScreen();
                });
                confirmScreen.openGui();
            }
        } catch (Exception e) {
            snoopypupser.buyingchunks.BuyingChunks.LOGGER.error("Failed to intercept sell action", e);
        }
    }
}
