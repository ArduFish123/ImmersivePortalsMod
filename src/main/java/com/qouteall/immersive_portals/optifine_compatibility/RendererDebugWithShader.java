package com.qouteall.immersive_portals.optifine_compatibility;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.OFInterface;
import com.qouteall.immersive_portals.portal.Portal;
import com.qouteall.immersive_portals.render.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlFramebuffer;
import net.minecraft.util.math.Vec3d;
import net.optifine.shaders.Shaders;
import org.lwjgl.opengl.GL13;

public class RendererDebugWithShader extends PortalRenderer {
    SecondaryFrameBuffer deferredBuffer = new SecondaryFrameBuffer();
    
    @Override
    public boolean shouldSkipClearing() {
        return false;
    }
    
    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
        if (Shaders.isShadowPass) {
            ViewAreaRenderer.drawPortalViewTriangle(portal);
        }
    }
    
    @Override
    public void onBeforeTranslucentRendering() {
    
    }
    
    @Override
    public void onAfterTranslucentRendering() {
        renderPortals();
    }
    
    @Override
    public void prepareRendering() {
        if (CGlobal.shaderManager == null) {
            CGlobal.shaderManager = new ShaderManager();
        }
        
        deferredBuffer.prepare();
        
        deferredBuffer.fb.setClearColor(1, 0, 0, 0);
        deferredBuffer.fb.clear(MinecraftClient.IS_SYSTEM_MAC);
    
        OFInterface.bindToShaderFrameBuffer.run();
    
    }
    
    @Override
    public void finishRendering() {
    
    }
    
    @Override
    protected void doRenderPortal(Portal portal) {
        if (MyRenderHelper.getRenderedPortalNum() >= 1) {
            return;
        }
        
        portalLayers.push(portal);
        
        manageCameraAndRenderPortalContent(portal);
        //it will bind the gbuffer of rendered dimension
        
        portalLayers.pop();
        
        deferredBuffer.fb.beginWrite(true);
        
        GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        mc.getFramebuffer().draw(
            deferredBuffer.fb.viewWidth,
            deferredBuffer.fb.viewHeight
        );
    
        OFInterface.bindToShaderFrameBuffer.run();
    }
    
    @Override
    protected void renderPortalContentWithContextSwitched(
        Portal portal, Vec3d oldCameraPos
    ) {
        OFGlobal.shaderContextManager.switchContextAndRun(
            () -> {
                OFInterface.bindToShaderFrameBuffer.run();
                super.renderPortalContentWithContextSwitched(portal, oldCameraPos);
            }
        );
    }
    
    @Override
    public void onRenderCenterEnded() {
        if (isRendering()) {
            return;
        }
    
        if (MyRenderHelper.getRenderedPortalNum() == 0) {
            return;
        }
        
        GlStateManager.enableAlphaTest();
        GlFramebuffer mainFrameBuffer = mc.getFramebuffer();
        mainFrameBuffer.beginWrite(true);
        
        deferredBuffer.fb.draw(mainFrameBuffer.viewWidth, mainFrameBuffer.viewHeight);
    }
}
