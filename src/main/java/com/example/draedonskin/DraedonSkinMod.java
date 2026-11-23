package com.example.draedonskin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(DraedonSkinMod.MODID)
public class DraedonSkinMod {
    public static final String MODID = "draedon_skin";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientEvents {
        @SuppressWarnings({"unchecked", "rawtypes"})
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            // La entidad del holograma esta registrada como exo_mech:hologram
            EntityType type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("exo_mech", "hologram"));
            if (type == null || !ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation("exo_mech", "hologram"))) {
                LOGGER.error("No se encontro exo_mech:hologram; no se aplica la skin de Draedon.");
                return;
            }
            event.registerEntityRenderer(type, DraedonSkinRenderer::new);
        }
    }
}
