package snoopypupser.buyingchunks.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import snoopypupser.buyingchunks.BuyingChunks;

@Mod(value = BuyingChunks.MOD_ID, dist = Dist.CLIENT)
public class BuyingChunksClientSetup {

    public BuyingChunksClientSetup(IEventBus modEventBus) {
        modEventBus.addListener(RegisterKeyMappingsEvent.class, ModKeyMappings::register);
        new ClaimShopRenderer().register();
        new BuyableChunkOverlay().register();
    }
}