package snoopypupser.buyingchunks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import snoopypupser.buyingchunks.BuyingChunks;
import snoopypupser.buyingchunks.claimshop.ClaimShopEntry;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;
import snoopypupser.buyingchunks.config.BuyingChunksConfig;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class ChunkBorderRenderer {

    private boolean visible = true;
    private int particleTick = 0;
    private UUID myTeamId = null;

    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();

        if (ModKeyMappings.TOGGLE_OVERLAY.consumeClick()) {
            visible = !visible;
        }

        if (mc.player == null || mc.level == null) return;

        Level level = mc.player.level();
        ResourceLocation dim = level.dimension().location();
        Map<ChunkPos, ClaimShopEntry> chunks = ClientClaimShopData.getAllForDimension(dim);
        if (chunks == null || chunks.isEmpty()) return;

        ChunkPos playerChunk = new ChunkPos(mc.player.blockPosition());
        int pcx = playerChunk.x;
        int pcz = playerChunk.z;

        int renderRadius = BuyingChunksConfig.PARTICLE_RADIUS.getAsInt();

        if (myTeamId == null && mc.player != null) {
            try {
                UUID pid = mc.player.getUUID();
                for (Team t : FTBTeamsAPI.api().getClientManager().getTeams()) {
                    Collection<UUID> members = t.getMembers();
                    if (members != null && members.contains(pid)) {
                        myTeamId = t.getId();
                        break;
                    }
                }
            } catch (Exception e) {
                BuyingChunks.LOGGER.warn("ChunkBorderRenderer: failed to get my team ID", e);
            }
        }

        Map<String, Integer> liveColors = new java.util.HashMap<>();
        try {
            for (Team t : FTBTeamsAPI.api().getClientManager().getTeams()) {
                String name = t.getName().getString();
                int c = t.getProperty(TeamProperties.COLOR).rgb() & 0xFFFFFF;
                liveColors.put(name, c);
            }
        } catch (Exception e) {
            BuyingChunks.LOGGER.warn("ChunkBorderRenderer: failed to get live team colors", e);
        }

        // --- PARTICLE BORDER (toggle-gated) ---
        if (visible) {
            particleTick++;
            int step = BuyingChunksConfig.PARTICLE_STEP.getAsInt();

            if (particleTick % BuyingChunksConfig.PARTICLE_INTERVAL.getAsInt() == 0) {
                for (Map.Entry<ChunkPos, ClaimShopEntry> entry : chunks.entrySet()) {
                    ChunkPos pos = entry.getKey();
                    if (Math.abs(pos.x - pcx) > renderRadius || Math.abs(pos.z - pcz) > renderRadius) continue;

                    ClaimShopEntry shopEntry = entry.getValue();
                    int teamColor = liveColors.getOrDefault(shopEntry.getShopTeamName(), shopEntry.getTeamColor());
                    float r = ((teamColor >> 16) & 0xFF) / 255.0f;
                    float g = ((teamColor >> 8) & 0xFF) / 255.0f;
                    float b = (teamColor & 0xFF) / 255.0f;
                    boolean isOwnListing = myTeamId != null && shopEntry.getSellerUUID() != null && myTeamId.equals(shopEntry.getSellerUUID());

                    int cx = pos.x * 16;
                    int cz = pos.z * 16;

                    ColorParticleOption colorOption = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, r, g, b);

                    for (int xo = 0; xo < 16; xo += step) {
                        int y1 = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + xo, cz);
                        int y2 = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + xo, cz + 15);
                        level.addParticle(colorOption, cx + xo + 0.5, y1 + 0.3, cz + 0.5, 0, 0, 0);
                        level.addParticle(colorOption, cx + xo + 0.5, y2 + 0.3, cz + 15 + 0.5, 0, 0, 0);
                    }
                    for (int zo = 0; zo < 16; zo += step) {
                        int y1 = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz + zo);
                        int y2 = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + 15, cz + zo);
                        level.addParticle(colorOption, cx + 0.5, y1 + 0.3, cz + zo + 0.5, 0, 0, 0);
                        level.addParticle(colorOption, cx + 15 + 0.5, y2 + 0.3, cz + zo + 0.5, 0, 0, 0);
                    }

                    if (isOwnListing) {
                        double ex = cx + level.random.nextDouble() * 16;
                        double ez = cz + level.random.nextDouble() * 16;
                        int ey = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(ex), (int) Math.floor(ez)) + 1;
                        level.addParticle(ParticleTypes.END_ROD, ex + 0.5, ey + 0.5 + level.random.nextDouble(), ez + 0.5,
                                (level.random.nextDouble() - 0.5) * 0.02,
                                0.03 + level.random.nextDouble() * 0.05,
                                (level.random.nextDouble() - 0.5) * 0.02);
                    }
                }
            }
        }
    }
}