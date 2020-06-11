package com.github.alexthe666.iceandfire.client.render.entity.layer;

import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.TabulaModel;
import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Vector3f;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.SegmentedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.item.BannerItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class LayerDragonBanner extends LayerRenderer<EntityDragonBase, SegmentedModel<EntityDragonBase>> {


    private final IEntityRenderer<EntityDragonBase, SegmentedModel<EntityDragonBase>> renderer;

    public LayerDragonBanner(MobRenderer renderIn) {
        super(renderIn);
        this.renderer = renderIn;
    }

    @Override
    public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, EntityDragonBase entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack itemstack = entity.getHeldItem(Hand.OFF_HAND);
        matrixStackIn.push();
        if (!itemstack.isEmpty() && itemstack.getItem() instanceof BannerItem) {
            float f = (entity.getRenderSize() / 3F);
            float f2 = 1F / f;
            matrixStackIn.push();
            matrixStackIn.translate(0, -0.125F, 0.4F);
            postRender(((TabulaModel) this.renderer.getEntityModel()).getCube("BodyUpper"), matrixStackIn, 0.0625F);
            Minecraft.getInstance().getItemRenderer().renderItem(itemstack, ItemCameraTransforms.TransformType.FIXED, packedLightIn, OverlayTexture.NO_OVERLAY, matrixStackIn, bufferIn);
            matrixStackIn.pop();
        }
        matrixStackIn.pop();
    }

    protected void postRender(AdvancedModelBox renderer, MatrixStack matrixStackIn, float scale) {
        if (renderer.rotateAngleX == 0.0F && renderer.rotateAngleY == 0.0F && renderer.rotateAngleZ == 0.0F) {
            if (renderer.rotationPointX != 0.0F || renderer.rotationPointY != 0.0F || renderer.rotationPointZ != 0.0F) {
                matrixStackIn.translate(renderer.rotationPointX * scale, renderer.rotationPointY * scale, renderer.rotationPointZ * scale);
            }
        } else {
            matrixStackIn.translate(renderer.rotationPointX * scale, renderer.rotationPointY * scale, renderer.rotationPointZ * scale);
            if (renderer.rotateAngleZ != 0.0F) {
                matrixStackIn.rotate(Vector3f.ZP.rotation(renderer.rotateAngleZ));
            }

            if (renderer.rotateAngleY != 0.0F) {
                matrixStackIn.rotate(Vector3f.YP.rotation(renderer.rotateAngleY));
            }

            if (renderer.rotateAngleX != 0.0F) {
                matrixStackIn.rotate(Vector3f.XP.rotation(renderer.rotateAngleX));
            }
        }
    }

}