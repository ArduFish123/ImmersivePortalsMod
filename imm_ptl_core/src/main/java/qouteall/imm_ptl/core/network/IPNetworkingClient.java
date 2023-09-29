package qouteall.imm_ptl.core.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.dimension.DimId;
import qouteall.q_misc_util.my_util.SignalArged;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public class IPNetworkingClient {
    
    public static final SignalArged<Portal> clientPortalSpawnSignal = new SignalArged<>();
    private static final Minecraft client = Minecraft.getInstance();
    
    public static void init() {
    
    }
    
    private static void processStcSpawnEntity(FriendlyByteBuf buf) {
        String entityTypeString = buf.readUtf();
        
        int entityId = buf.readInt();
        
        ResourceKey<Level> dim = DimId.readWorldId(buf, true);
        
        CompoundTag compoundTag = buf.readNbt();
        
        processEntitySpawn(entityTypeString, entityId, dim, compoundTag);
    }
    
    private static void processStcDimensionConfirm(FriendlyByteBuf buf) {
        
        ResourceKey<Level> dimension = DimId.readWorldId(buf, true);
        Vec3 pos = new Vec3(
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble()
        );
        
        MiscHelper.executeOnRenderThread(() -> {
            ClientTeleportationManager.acceptSynchronizationDataFromServer(
                dimension, pos,
                false
            );
        });
    }
    
    private static void processGlobalPortalUpdate(FriendlyByteBuf buf) {
        ResourceKey<Level> dimension = DimId.readWorldId(buf, true);
        CompoundTag compoundTag = buf.readNbt();
        MiscHelper.executeOnRenderThread(() -> {
            GlobalPortalStorage.receiveGlobalPortalSync(dimension, compoundTag);
        });
    }
    
//    public static Packet createCtsPlayerAction(
//        ResourceKey<Level> dimension,
//        ServerboundPlayerActionPacket packet
//    ) {
//        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
//        DimId.writeWorldId(buf, dimension, true);
//        packet.write(buf);
//        return new ServerboundCustomPayloadPacket(IPNetworking.id_ctsPlayerAction, buf);
//    }
//
//    public static Packet createCtsRightClick(
//        ResourceKey<Level> dimension,
//        ServerboundUseItemOnPacket packet
//    ) {
//        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
//        DimId.writeWorldId(buf, dimension, true);
//        packet.write(buf);
//        return new ServerboundCustomPayloadPacket(IPNetworking.id_ctsRightClick, buf);
//    }
    
    public static Packet createCtsTeleport(
        ResourceKey<Level> dimensionBefore,
        Vec3 posBefore,
        UUID portalEntityId
    ) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        DimId.writeWorldId(buf, dimensionBefore, true);
        buf.writeDouble(posBefore.x);
        buf.writeDouble(posBefore.y);
        buf.writeDouble(posBefore.z);
        buf.writeUUID(portalEntityId);
        return new ServerboundCustomPayloadPacket(IPNetworking.id_ctsTeleport, buf);
    }
    
    public static void processEntitySpawn(String entityTypeString, int entityId, ResourceKey<Level> dim, CompoundTag compoundTag) {
    
    }
}
