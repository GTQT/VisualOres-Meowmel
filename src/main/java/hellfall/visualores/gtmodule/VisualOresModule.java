package hellfall.visualores.gtmodule;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.modules.GregTechModule;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler;
import gregtech.common.blocks.BlockOre;
import gregtech.common.blocks.BlockSurfaceRock;
import gregtech.modules.BaseGregTechModule;
import hellfall.visualores.Tags;
import hellfall.visualores.VOConfig;
import hellfall.visualores.VisualOres;
import hellfall.visualores.database.ClientCacheManager;
import hellfall.visualores.database.gregtech.GTClientCache;
import hellfall.visualores.database.gregtech.ore.OreVeinInfo;
import hellfall.visualores.database.gregtech.ore.ServerCache;
import hellfall.visualores.map.layers.Layers;
import hellfall.visualores.map.layers.gregtech.OreRenderLayer;
import hellfall.visualores.map.layers.gregtech.UndergroundFluidRenderLayer;
import hellfall.visualores.network.gregtech.FluidSaveVersionPacket;
import hellfall.visualores.network.gregtech.OreProspectToClientPacket;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

@GregTechModule(
        moduleID = VisualOresModuleContainer.VO_MODULE,
        containerID = GTValues.MODID,
        name = "VisualOres module",
        coreModule = true,
        description = "VisualOres GregTech Integration. Disabling this will disable all GT integration in VisualOres."
)
public class VisualOresModule extends BaseGregTechModule {
    @Nonnull
    @Override
    public List<Class<?>> getEventBusSubscribers() {
        return Collections.singletonList(VisualOresModule.class);
    }

    @Nonnull
    @Override
    public Logger getLogger() {
        return VisualOres.LOGGER;
    }

    @Override
    public void registerPackets() {
        GregTechAPI.networkHandler.registerPacket(OreProspectToClientPacket.class);
        GregTechAPI.networkHandler.registerPacket(FluidSaveVersionPacket.class);
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        if (GTValues.isClientSide()) {
            Layers.registerLayer(OreRenderLayer.class, "oreveins");
            Layers.registerLayer(UndergroundFluidRenderLayer.class, "undergroundfluid");

            ClientCacheManager.registerClientCache(GTClientCache.instance, "gregtech");
        }
    }

    @Override
    public void serverStopped(FMLServerStoppedEvent event) {
        ServerCache.instance.clear();
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        if (!event.getWorld().isRemote) {
            ServerCache.instance.maybeInitWorld(event.getWorld());
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            ServerCache.instance.invalidateWorld(event.getWorld());
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote && event.getEntity() instanceof EntityPlayerMP player) {
            GregTechAPI.networkHandler.sendTo(new FluidSaveVersionPacket(BedrockFluidVeinHandler.saveDataVersion), player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide() == Side.SERVER && event.getEntityPlayer() instanceof EntityPlayerMP player) {
            IBlockState state = event.getWorld().getBlockState(event.getPos());
            if (state.getBlock() instanceof BlockOre) {
                Material oreMaterial = OreVeinInfo.getMaterial(state);
                if (!oreMaterial.equals(Materials.NULL)) {
                    ServerCache.instance.prospectByOreMaterial(
                            event.getWorld().provider.getDimension(),
                            oreMaterial,
                            event.getPos(),
                            player,
                            VOConfig.server.gregtech.oreBlockProspectRange
                    );
                }
            }
            else if (state.getBlock() instanceof BlockSurfaceRock block) {
                ServerCache.instance.prospectSurfaceRockMaterial(
                        event.getWorld().provider.getDimension(),
                        state.getValue(block.getVariantProperty()),
                        event.getPos(),
                        player
                );
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getSide() == Side.SERVER && event.getEntityPlayer() instanceof EntityPlayerMP player) {
            IBlockState state = event.getWorld().getBlockState(event.getPos());
            if (state.getBlock() instanceof BlockSurfaceRock block) {
                ServerCache.instance.prospectSurfaceRockMaterial(
                        event.getWorld().provider.getDimension(),
                        state.getValue(block.getVariantProperty()),
                        event.getPos(),
                        player
                );
            }
        }
    }
}
