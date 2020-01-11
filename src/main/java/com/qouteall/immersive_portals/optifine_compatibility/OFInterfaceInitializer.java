package com.qouteall.immersive_portals.optifine_compatibility;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qouteall.immersive_portals.IEOFWorldRenderer;
import com.qouteall.immersive_portals.OFInterface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.optifine.Config;
import net.optifine.shaders.Shaders;
import org.apache.commons.lang3.Validate;
import org.lwjgl.opengl.EXTFramebufferObject;

import java.lang.reflect.Field;

public class OFInterfaceInitializer {
    private static Field gameRenderer_fogStandard;
    
    public static void init() {
        Validate.isTrue(OFInterface.isOptifinePresent);
        
        OFInterface.isShaders = Config::isShaders;
        OFInterface.isShadowPass = () -> Config.isShaders() && Shaders.isShadowPass;
        OFInterface.bindToShaderFrameBuffer = () -> {
            EXTFramebufferObject.glBindFramebufferEXT(36160, OFGlobal.getDfb.get());
            GlStateManager.viewport(0, 0, Shaders.renderWidth, Shaders.renderHeight);
        };
        OFInterface.beforeRenderCenter = (partialTicks) -> {
            if (Config.isShaders()) {
                MinecraftClient mc = MinecraftClient.getInstance();
        
                Shaders.activeProgram = Shaders.ProgramNone;
                Shaders.beginRender(mc, mc.gameRenderer.getCamera(), partialTicks, 0);
            }
            
        };
        OFInterface.afterRenderCenter = () -> Shaders.activeProgram = Shaders.ProgramNone;
        OFInterface.resetViewport = () -> {
            if (OFInterface.isShaders.getAsBoolean()) {
                GlStateManager.viewport(0, 0, Shaders.renderWidth, Shaders.renderHeight);
            }
        };
        OFInterface.onPlayerTraveled = (fromDimension1, toDimension1) -> {
            if (OFInterface.isShaders.getAsBoolean()) {
                OFGlobal.shaderContextManager.onPlayerTraveled(
                    fromDimension1,
                    toDimension1
                );
            }
        };
        OFInterface.shouldDisableFog = () -> {
            GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
    
            if (gameRenderer_fogStandard == null) {
                try {
                    gameRenderer_fogStandard = GameRenderer.class.getDeclaredField(
                        "fogStandard"
                    );
                }
                catch (NoSuchFieldException e) {
                    throw new IllegalStateException(e);
                }
            }
    
            try {
                boolean fogStandard = gameRenderer_fogStandard.getBoolean(gameRenderer);
        
                return Config.isFogOff() && fogStandard;
            }
            catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        };
        OFInterface.createNewRenderInfosNormal = newWorldRenderer1 -> {
            /**{@link WorldRenderer#chunkInfos}*/
            //in vanilla it will create new chunkInfos object every frame
            //but with optifine it will always use one object
            //we need to switch chunkInfos correctly
            //if we do not put it a new object, it will clear the original chunkInfos
            ((IEOFWorldRenderer) newWorldRenderer1).createNewRenderInfosNormal();
        };
        OFInterface.initShaderCullingManager = ShaderCullingManager::init;
    }
}
