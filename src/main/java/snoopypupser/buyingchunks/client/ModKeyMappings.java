package snoopypupser.buyingchunks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class ModKeyMappings {
    public static final KeyMapping BUY_KEY = new KeyMapping(
        "key.buyingchunks.buy",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        "key.categories.buyingchunks"
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(BUY_KEY);
    }
}
