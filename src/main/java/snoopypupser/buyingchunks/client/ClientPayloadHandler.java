package snoopypupser.buyingchunks.client;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import snoopypupser.buyingchunks.claimshop.ClientClaimShopData;
import snoopypupser.buyingchunks.network.BuyErrorPacket;
import snoopypupser.buyingchunks.network.ListingToastPacket;
import snoopypupser.buyingchunks.network.OpenAdminScreenPacket;
import snoopypupser.buyingchunks.network.OpenMainDashboardScreenPacket;
import snoopypupser.buyingchunks.network.PurchaseEffectPacket;
import snoopypupser.buyingchunks.network.TeamListRefreshPacket;

public class ClientPayloadHandler {

    public static void handleListingToast(ListingToastPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Item item = BuiltInRegistries.ITEM.get(packet.priceItemId());
            Component title = Component.translatable("uc7core.claimshop.toast.listed.title");
            Component body = Component.translatable("uc7core.claimshop.toast.listed.body",
                    packet.chunkX(), packet.chunkZ(),
                    packet.priceCount(),
                    item.getDescription());
            mc.getToasts().addToast(new SystemToast(
                    new SystemToast.SystemToastId(),
                    title, body
            ));
        });
    }

    public static void handlePurchaseEffect(PurchaseEffectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            int cx = packet.chunkX() * 16;
            int cz = packet.chunkZ() * 16;
            int centerX = cx + 8;
            int centerZ = cz + 8;
            int baseY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);

            float red = ((packet.teamColor() >> 16) & 0xFF) / 255.0f;
            float green = ((packet.teamColor() >> 8) & 0xFF) / 255.0f;
            float blue = (packet.teamColor() & 0xFF) / 255.0f;
            ColorParticleOption colorOption = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, red, green, blue);

            double[][] launchSites = {
                {cx + 8, cz},
                {cx + 8, cz + 15},
                {cx, cz + 8},
                {cx + 15, cz + 8}
            };

            int ascentDuration = 100;
            double[] targetHeights = new double[4];
            for (int i = 0; i < 4; i++) {
                targetHeights[i] = baseY + 15 + mc.level.random.nextDouble() * 5;
            }

            Thread animThread = new Thread(() -> {
                int[] launchTicks = {0, 20, 40, 60};

                for (int t = 0; t <= 140; t++) {
                    final int currentTick = t;
                    mc.execute(() -> {
                        if (mc.level == null) return;

                        for (int r = 0; r < 4; r++) {
                            int rocketTick = currentTick - launchTicks[r];
                            if (rocketTick < 0 || rocketTick > ascentDuration + 3) continue;

                            double lx = launchSites[r][0] + 0.5;
                            double lz = launchSites[r][1] + 0.5;

                            if (rocketTick < ascentDuration) {
                                double progress = (double) rocketTick / ascentDuration;
                                double ty = baseY + 0.5 + progress * (targetHeights[r] - baseY);
                                mc.level.addParticle(ParticleTypes.FIREWORK, lx, ty, lz,
                                        (mc.level.random.nextDouble() - 0.5) * 0.02,
                                        0.02 + mc.level.random.nextDouble() * 0.03,
                                        (mc.level.random.nextDouble() - 0.5) * 0.02);
                            } else if (rocketTick == ascentDuration) {
                                double ey = targetHeights[r];
                                mc.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, lx, ey, lz, 0, 0, 0);

                                for (int i = 0; i < 25; i++) {
                                    double speed = 0.3 + mc.level.random.nextDouble() * 0.5;
                                    double theta = mc.level.random.nextDouble() * Math.PI * 2;
                                    double phi = mc.level.random.nextDouble() * Math.PI * 2;
                                    mc.level.addParticle(ParticleTypes.FIREWORK, lx, ey, lz,
                                            Math.sin(phi) * Math.cos(theta) * speed,
                                            Math.sin(phi) * Math.sin(theta) * speed,
                                            Math.cos(phi) * speed);
                                }
                                for (int i = 0; i < 15; i++) {
                                    double speed = 0.15 + mc.level.random.nextDouble() * 0.25;
                                    double theta = mc.level.random.nextDouble() * Math.PI * 2;
                                    double phi = mc.level.random.nextDouble() * Math.PI * 2;
                                    mc.level.addParticle(colorOption, lx, ey, lz,
                                            Math.sin(phi) * Math.cos(theta) * speed,
                                            Math.sin(phi) * Math.sin(theta) * speed,
                                            Math.cos(phi) * speed);
                                }
                                for (int i = 0; i < 12; i++) {
                                    double speed = 0.4 + mc.level.random.nextDouble() * 0.6;
                                    double theta = mc.level.random.nextDouble() * Math.PI * 2;
                                    double phi = mc.level.random.nextDouble() * Math.PI * 2;
                                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, lx, ey, lz,
                                            Math.sin(phi) * Math.cos(theta) * speed,
                                            Math.sin(phi) * Math.sin(theta) * speed,
                                            Math.cos(phi) * speed);
                                }

                                mc.level.playLocalSound((int) lx, (int) ey, (int) lz,
                                        SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0f, 1.0f, false);
                            }
                        }
                    });

                    try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                }

                mc.execute(() -> {
                    if (mc.level == null) return;
                    mc.level.playLocalSound(centerX, baseY + 1, centerZ,
                            SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.0f, 1.2f, false);
                    mc.getToasts().addToast(new SystemToast(
                            new SystemToast.SystemToastId(),
                            Component.translatable("uc7core.claimshop.toast.purchased.title"),
                            Component.translatable("uc7core.claimshop.toast.purchased.body", packet.chunkX(), packet.chunkZ())
                    ));
                });
            });
            animThread.setDaemon(true);
            animThread.start();
        });
    }

    public static void handleBuyError(BuyErrorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Component msg = Component.Serializer.fromJson(packet.messageJson(), mc.level.registryAccess());
                if (msg != null && mc.player != null) {
                    mc.player.sendSystemMessage(msg);
                }
            }
        });
    }

    public static void handleTeamListRefresh(TeamListRefreshPacket packet, IPayloadContext context) {
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

    public static void handleOpenAdminScreen(OpenAdminScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                new AdminDashboardScreen().openGui()
        );
    }

    public static void handleOpenMainDashboardScreen(OpenMainDashboardScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientClaimShopData.setPendingIncome(packet.pendingIncome());
            ClientClaimShopData.setQuickbuyEnabled(packet.quickbuyEnabled());
            new MainDashboardScreen().openGui();
        });
    }
}
