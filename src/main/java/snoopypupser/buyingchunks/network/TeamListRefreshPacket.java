package snoopypupser.buyingchunks.network;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.client.AdminServerTeamMgmtScreen;

public record TeamListRefreshPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TeamListRefreshPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BuyingChunks.MOD_ID, "team_list_refresh"));

    public static final StreamCodec<FriendlyByteBuf, TeamListRefreshPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {}, buf -> new TeamListRefreshPacket());

    public static void handle(TeamListRefreshPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof ScreenWrapper wrapper) {
                BaseScreen gui = wrapper.getGui();
                if (gui instanceof AdminServerTeamMgmtScreen mgmtScreen) {
                    mgmtScreen.refreshTeamList();
                }
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
