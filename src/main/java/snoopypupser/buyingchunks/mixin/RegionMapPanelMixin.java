package snoopypupser.buyingchunks.mixin;

import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftbchunks.client.map.MapRegionData;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(RegionMapPanel.class)
public class RegionMapPanelMixin {

    @Inject(method = "addMouseOverText",
            at = @At(value = "INVOKE",
                     target = "Ldev/ftb/mods/ftblibrary/util/TooltipList;add(Lnet/minecraft/network/chat/Component;)V",
                     shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILSOFT,
            remap = false)
    private void onAddMouseOverText(TooltipList list, CallbackInfo ci,
                                    MapRegion region, MapRegionData data,
                                    MapChunk chunk, Team team) {
        if (team == null) return;

        String desc;
        try {
            desc = team.getProperty(TeamProperties.DESCRIPTION);
        } catch (Exception e) {
            return;
        }
        if (desc == null || desc.isEmpty()) return;

        list.add(Component.literal(desc)
                .withStyle(Style.EMPTY.withColor(0xAAAAAA).withItalic(true)));
    }
}
