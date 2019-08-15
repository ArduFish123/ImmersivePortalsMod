package com.qouteall.immersive_portals;

import com.qouteall.immersive_portals.chunk_loading.MyClientChunkManager;
import com.qouteall.immersive_portals.exposer.IEClientPlayNetworkHandler;
import com.qouteall.immersive_portals.exposer.IEClientWorld;
import com.qouteall.immersive_portals.my_util.Helper;
import com.qouteall.immersive_portals.my_util.ICustomStcPacket;
import com.qouteall.immersive_portals.portal.LoadingIndicatorEntity;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.fabricmc.fabric.api.network.PacketContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.packet.CustomPayloadS2CPacket;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Packet;
import net.minecraft.server.network.packet.CustomPayloadC2SPacket;
import net.minecraft.util.PacketByteBuf;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionType;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Optional;

public class MyNetworkClient {
    
    //you can input a lambda expression and it will be invoked remotely
    //but java serialization is not stable
    @Deprecated
    public static CustomPayloadS2CPacket createCustomPacketStc(
        ICustomStcPacket serializable
    ) {
        //it copies the data twice but as the packet is small it's of no problem
        
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream stream = null;
        try {
            stream = new ObjectOutputStream(byteArrayOutputStream);
            stream.writeObject(serializable);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBytes(byteArrayOutputStream.toByteArray());
        
        PacketByteBuf buf = new PacketByteBuf(buffer);
    
        return new CustomPayloadS2CPacket(MyNetworkServer.id_stcCustom, buf);
    }
    
    @Deprecated
    private static void handleCustomPacketStc(PacketContext context, PacketByteBuf buf) {
        ByteBuffer byteBuffer = buf.nioBuffer();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteBuffer.array());
        ObjectInputStream objectInputStream;
        try {
            objectInputStream = new ObjectInputStream(byteArrayInputStream);
            Object obj = objectInputStream.readObject();
            ICustomStcPacket customStcPacket = (ICustomStcPacket) obj;
            customStcPacket.handle();
        }
        catch (IOException | ClassNotFoundException | ClassCastException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    public static CustomPayloadC2SPacket createCtsTeleport(
        DimensionType dimensionBefore,
        Vec3d posBefore,
        int portalEntityId
    ) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(dimensionBefore.getRawId());
        buf.writeDouble(posBefore.x);
        buf.writeDouble(posBefore.y);
        buf.writeDouble(posBefore.z);
        buf.writeInt(portalEntityId);
        return new CustomPayloadC2SPacket(MyNetworkServer.id_ctsTeleport, buf);
    }
    
    //NOTE my packet is redirected but I cannot get the packet handler info here
    public static CustomPayloadS2CPacket createStcSpawnEntity(
        Entity entity
    ) {
        EntityType entityType = entity.getType();
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(EntityType.getId(entityType).toString());
        buf.writeInt(entity.getEntityId());
        buf.writeInt(entity.dimension.getRawId());
        CompoundTag tag = new CompoundTag();
        entity.toTag(tag);
        buf.writeCompoundTag(tag);
        return new CustomPayloadS2CPacket(MyNetworkServer.id_stcSpawnEntity, buf);
    }
    
    private static void processStcSpawnEntity(PacketContext context, PacketByteBuf buf) {
        String entityTypeString = buf.readString();
        int entityId = buf.readInt();
        DimensionType dimensionType = DimensionType.byRawId(buf.readInt());
        CompoundTag compoundTag = buf.readCompoundTag();
    
        Optional<EntityType<?>> entityType = EntityType.get(entityTypeString);
        if (!entityType.isPresent()) {
            Helper.err("unknown entity type " + entityTypeString);
            return;
        }
    
        ModMain.clientTaskList.addTask(() -> {
            ClientWorld world = CGlobal.clientWorldLoader.getOrCreateFakedWorld(dimensionType);
            
            if (world.getEntityById(entityId) != null) {
                Helper.err(String.format(
                    "duplicate entity %s %s %s",
                    ((Integer) entityId).toString(),
                    entityType.get(),
                    compoundTag
                ));
                return true;
            }
            
            Entity entity = entityType.get().create(
                world
            );
            entity.fromTag(compoundTag);
            entity.setEntityId(entityId);
            entity.updateTrackedPosition(entity.x, entity.y, entity.z);
            world.addEntity(entityId, entity);
            
            return true;
        });
    }
    
    public static CustomPayloadS2CPacket createStcDimensionConfirm(
        DimensionType dimensionType,
        Vec3d pos
    ) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(dimensionType.getRawId());
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        return new CustomPayloadS2CPacket(MyNetworkServer.id_stcDimensionConfirm, buf);
    }
    
    private static void processStcDimensionConfirm(PacketContext context, PacketByteBuf buf) {
        DimensionType dimension = DimensionType.byRawId(buf.readInt());
        Vec3d pos = new Vec3d(
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble()
        );
    
        MinecraftClient.getInstance().execute(() -> {
            CGlobal.clientTeleportationManager.acceptSynchronizationDataFromServer(
                dimension, pos
            );
        });
    }
    
    public static CustomPayloadS2CPacket createSpawnLoadingIndicator(
        DimensionType dimensionType,
        Vec3d pos
    ) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(dimensionType.getRawId());
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        return new CustomPayloadS2CPacket(MyNetworkServer.id_stcSpawnLoadingIndicator, buf);
    }
    
    private static void processSpawnLoadingIndicator(PacketContext context, PacketByteBuf buf) {
        DimensionType dimension = DimensionType.byRawId(buf.readInt());
        Vec3d pos = new Vec3d(
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble()
        );
        
        MinecraftClient.getInstance().execute(() -> {
            ClientWorld world = CGlobal.clientWorldLoader.getDimension(dimension);
            if (world == null) {
                return;
            }
            
            LoadingIndicatorEntity indicator = new LoadingIndicatorEntity(world);
            indicator.setPosition(pos.x, pos.y, pos.z);
            
            world.addEntity(233333333, indicator);
        });
    }
    
    public static void init() {
    
    
        ClientSidePacketRegistry.INSTANCE.register(
            MyNetworkServer.id_stcCustom,
            MyNetworkClient::handleCustomPacketStc
        );
        
        ClientSidePacketRegistry.INSTANCE.register(
            MyNetworkServer.id_stcSpawnEntity,
            MyNetworkClient::processStcSpawnEntity
        );
        
        ClientSidePacketRegistry.INSTANCE.register(
            MyNetworkServer.id_stcDimensionConfirm,
            MyNetworkClient::processStcDimensionConfirm
        );
        
        ClientSidePacketRegistry.INSTANCE.register(
            MyNetworkServer.id_stcRedirected,
            MyNetworkClient::processRedirectedMessage
        );
    
        ClientSidePacketRegistry.INSTANCE.register(
            MyNetworkServer.id_stcSpawnLoadingIndicator,
            MyNetworkClient::processSpawnLoadingIndicator
        );
    }
    
    public static void processRedirectedMessage(
        PacketContext context,
        PacketByteBuf buf
    ) {
        int dimensionId = buf.readInt();
        int messageType = buf.readInt();
        DimensionType dimension = DimensionType.byRawId(dimensionId);
        Packet packet = MyNetworkServer.createEmptyPacketByType(messageType);
        try {
            packet.read(buf);
        }
        catch (IOException e) {
            assert false;
            throw new IllegalArgumentException();
        }
        
        processRedirectedPacket(dimension, packet);
    }
    
    private static void processRedirectedPacket(DimensionType dimension, Packet packet) {
        MinecraftClient.getInstance().execute(() -> {
            ClientWorld clientWorld = Helper.loadClientWorld(dimension);
            
            assert clientWorld != null;
            
            assert clientWorld.getChunkManager() instanceof MyClientChunkManager;
            
            ClientPlayNetworkHandler netHandler = ((IEClientWorld) clientWorld).getNetHandler();
            
            if ((netHandler).getWorld() != clientWorld) {
                ((IEClientPlayNetworkHandler) netHandler).setWorld(clientWorld);
            }
            
            packet.apply(netHandler);
        });
    }
}
