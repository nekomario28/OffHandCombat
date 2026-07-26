package dev.nekomario.offhandcombat;

import com.mojang.logging.LogUtils;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.config.OffHandCombatClientConfig;
import dev.nekomario.offhandcombat.config.OffHandCombatConfig;
import dev.nekomario.offhandcombat.network.OffHandCombatNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(OffHandCombat.MOD_ID)
public final class OffHandCombat {
    public static final String MOD_ID = "offhandcombat";
    public static final String PROTOCOL_VERSION = "2";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OffHandCombat(IEventBus modEventBus, ModContainer modContainer) {
        OffhandCombatAttachments.ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(OffHandCombatNetwork::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.COMMON, OffHandCombatConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, OffHandCombatClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
