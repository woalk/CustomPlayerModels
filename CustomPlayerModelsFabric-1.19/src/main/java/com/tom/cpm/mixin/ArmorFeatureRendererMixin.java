package com.tom.cpm.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.util.Identifier;

import com.tom.cpm.client.CustomPlayerModelsClient;
import com.tom.cpm.client.ModelTexture;
import com.tom.cpm.client.PlayerRenderManager;
import com.tom.cpm.shared.model.TextureSheetType;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin extends FeatureRenderer<LivingEntity, BipedEntityModel<LivingEntity>> {

	public ArmorFeatureRendererMixin(FeatureRendererContext<LivingEntity, BipedEntityModel<LivingEntity>> context) {
		super(context);
	}

	private @Final @Shadow BipedEntityModel<LivingEntity> innerModel;
	private @Final @Shadow BipedEntityModel<LivingEntity> outerModel;

	@Shadow abstract Identifier getArmorTexture(ArmorItem armorItem, boolean bl, String string);

	@Inject(at = @At("HEAD"),
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I"
					+ "Lnet/minecraft/entity/LivingEntity;FFFFFF)V")
	public void preRender(MatrixStack matrixStackIn, VertexConsumerProvider bufferIn, int packedLightIn, LivingEntity entitylivingbaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo cbi) {
		CustomPlayerModelsClient.INSTANCE.renderArmor(outerModel, innerModel, getContextModel());
	}

	@Inject(at = @At("RETURN"),
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I"
					+ "Lnet/minecraft/entity/LivingEntity;FFFFFF)V")
	public void postRender(MatrixStack matrixStackIn, VertexConsumerProvider bufferIn, int packedLightIn, LivingEntity entitylivingbaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo cbi) {
		CustomPlayerModelsClient.INSTANCE.manager.unbind(outerModel);
		CustomPlayerModelsClient.INSTANCE.manager.unbind(innerModel);
	}

	@Inject(at = @At("HEAD"),
			method = "renderArmorParts(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I"
					+ "Lnet/minecraft/item/ArmorItem;ZLnet/minecraft/client/render/entity/model/BipedEntityModel;ZFFFLjava/lang/String;)V")
	private void preRenderTexture(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i,
			ArmorItem armorItem, boolean bl, BipedEntityModel<LivingEntity> model, boolean bl2, float f, float g, float h,
			String string, CallbackInfo cbi) {
		CustomPlayerModelsClient.mc.getPlayerRenderManager().bindSkin(model, new ModelTexture(this.getArmorTexture(armorItem, bl2, string), PlayerRenderManager.armor), model == innerModel ? TextureSheetType.ARMOR2 : TextureSheetType.ARMOR1);
	}

	@Inject(at = @At("HEAD"),
			method = {"renderModel(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I"
					+ "ZLnet/minecraft/client/render/entity/model/BipedEntityModel;FFFLnet/minecraft/util/Identifier;)V",
					"renderModel(Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;I"
							+ "ZLnet/minecraft/class_572;FFFLnet/minecraft/class_2960;)V"},
			remap = false, require = 0)//Optifine
	private void preRenderTexture(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int p_241738_3_, boolean p_241738_5_, BipedEntityModel<LivingEntity> model, float p_241738_8_, float p_241738_9_, float p_241738_10_, Identifier resLoc, CallbackInfo cbi) {
		CustomPlayerModelsClient.mc.getPlayerRenderManager().bindSkin(model, new ModelTexture(resLoc, PlayerRenderManager.armor), model == innerModel ? TextureSheetType.ARMOR2 : TextureSheetType.ARMOR1);
	}

	@Inject(at = @At("HEAD"),
			method = {"renderModel(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I"
					+ "ZLnet/minecraft/client/model/Model;FFFLnet/minecraft/util/Identifier;)V",
					"renderModel(Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;I"
							+ "ZLnet/minecraft/class_3879;FFFLnet/minecraft/class_2960;)V"},
			remap = false, require = 0)//Optifine
	private void preRenderTexture(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int p_241738_3_, boolean p_241738_5_, net.minecraft.client.model.Model model, float p_241738_8_, float p_241738_9_, float p_241738_10_, Identifier resLoc, CallbackInfo cbi) {
		CustomPlayerModelsClient.mc.getPlayerRenderManager().bindSkin(model, new ModelTexture(resLoc, PlayerRenderManager.armor), model == innerModel ? TextureSheetType.ARMOR2 : TextureSheetType.ARMOR1);
	}
}
