package hellfall.visualores.mixins.gregtech;

import gregtech.api.gui.Widget;
import gregtech.api.util.Position;
import gregtech.api.util.Size;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler;
import gregtech.common.gui.widget.prospector.ProspectorMode;
import gregtech.common.gui.widget.prospector.widget.WidgetProspectingMap;
import gregtech.core.network.packets.PacketProspecting;
import hellfall.visualores.database.gregtech.GTClientCache;
import hellfall.visualores.database.gregtech.ore.ServerCache;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(WidgetProspectingMap.class)
public abstract class WidgetProspectingMapMixin extends Widget {

    @Shadow(remap = false)
    @Final
    private ProspectorMode mode;

    public WidgetProspectingMapMixin(Position selfPosition, Size size) {
        super(selfPosition, size);
    }

    @Redirect(
            method = "detectAndSendChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getChunk(II)Lnet/minecraft/world/chunk/Chunk;"
            )
    )
    private Chunk visualores$injectDASChanges(World world, int chunkX, int chunkZ) {
        try {
            if (gui != null && gui.entityPlayer instanceof EntityPlayerMP &&
                    mode == ProspectorMode.ORE) {

                ServerCache.instance.prospectAllInChunk(
                        world.provider.getDimension(),
                        new ChunkPos(chunkX, chunkZ),
                        (EntityPlayerMP) gui.entityPlayer
                );
            }
        } catch (Exception ignored) {
        }
        return world.getChunk(chunkX, chunkZ);
    }

    @Inject(
            method = "addPacketToQueue",
            at = @At("HEAD"),
            remap = false
    )
    private void visualores$handleFluidPacket(PacketProspecting packet, CallbackInfo ci) {
        if (packet == null || packet.mode != ProspectorMode.FLUID ||
                packet.map == null || packet.map.length == 0 ||
                packet.map[0] == null || packet.map[0].length == 0) {
            return;
        }

        Map<Byte, String> fluidData = packet.map[0][0];
        if (fluidData == null) return;

        String fluidName = fluidData.get((byte) 1);
        String yieldStr = fluidData.get((byte) 2);
        String depletionStr = fluidData.get((byte) 3);

        if (fluidName == null || yieldStr == null || depletionStr == null) {
            return;
        }

        try {
            int yield = Integer.parseInt(yieldStr);
            double depletion = Double.parseDouble(depletionStr);

            int fieldX = BedrockFluidVeinHandler.getVeinCoord(packet.chunkX);
            int fieldZ = BedrockFluidVeinHandler.getVeinCoord(packet.chunkZ);

            // 获取维度ID
            int dimension = gui != null && gui.entityPlayer != null && gui.entityPlayer.world != null ?
                    gui.entityPlayer.world.provider.getDimension() : 0;

            GTClientCache.instance.addFluid(
                    dimension,
                    fieldX,
                    fieldZ,
                    fluidName,
                    yield,
                    depletion
            );
        } catch (NumberFormatException ignored) {
        }
    }
}