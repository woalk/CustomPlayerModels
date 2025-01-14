package com.tom.cpm.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import com.tom.cpl.function.ToFloatFunction;
import com.tom.cpl.render.RenderTypeBuilder.TextureHandler;
import com.tom.cpl.text.FormatText;
import com.tom.cpm.shared.MinecraftClientAccess;
import com.tom.cpm.shared.animation.AnimationEngine.AnimationMode;
import com.tom.cpm.shared.animation.AnimationState;
import com.tom.cpm.shared.animation.CustomPose;
import com.tom.cpm.shared.animation.Gesture;
import com.tom.cpm.shared.config.Player;
import com.tom.cpm.shared.definition.ModelDefinition;
import com.tom.cpm.shared.editor.Generators;
import com.tom.cpm.shared.editor.gui.EditorGui;
import com.tom.cpm.shared.io.ModelFile;
import com.tom.cpm.shared.model.TextureSheetType;
import com.tom.cpm.shared.model.render.ModelRenderManager;

public class ClientApi extends SharedApi implements IClientAPI {
	private List<ToFloatFunction<Object>> voice = new ArrayList<>();
	private TextureHandlerFactory<?, ?> textureHandlerFactory;
	private BiFunction<UUID, String, ?> gameProfileFactory;

	@Override
	protected void callInit0(ICPMPlugin plugin) {
		plugin.initClient(this);
	}

	protected ClientApi() {
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> void registerVoice(Class<T> clazz, Function<T, Float> getVoiceLevel) {
		if(checkClass(clazz, Clazz.PLAYER))return;
		voice.add(p -> getVoiceLevel.apply((T) p));
	}

	public static class ApiBuilder {
		private final CPMApiManager api;

		protected ApiBuilder(CPMApiManager api) {
			this.api = api;
			api.client = new ClientApi();
		}

		public ApiBuilder voicePlayer(Class<?> player) {
			api.client.classes.put(Clazz.PLAYER, player);
			return this;
		}

		public <RL, RT> ApiBuilder renderApi(Class<?> playerModel, Class<RL> resLoc, Class<RT> renderType, Class<?> multiBuffer, Class<?> gp, TextureHandlerFactory<RL, RT> thf) {
			api.client.classes.put(Clazz.MODEL, playerModel);
			api.client.classes.put(Clazz.RESOURCE_LOCATION, resLoc);
			api.client.classes.put(Clazz.RENDER_TYPE, renderType);
			api.client.classes.put(Clazz.MULTI_BUFFER_SOURCE, multiBuffer);
			api.client.classes.put(Clazz.GAME_PROFILE, gp);
			api.client.textureHandlerFactory = thf;
			return this;
		}

		public ApiBuilder renderApi(Class<?> playerModel, Class<?> gp) {
			api.client.classes.put(Clazz.MODEL, playerModel);
			api.client.classes.put(Clazz.RESOURCE_LOCATION, Void.class);
			api.client.classes.put(Clazz.RENDER_TYPE, Void.class);
			api.client.classes.put(Clazz.MULTI_BUFFER_SOURCE, Void.class);
			api.client.classes.put(Clazz.GAME_PROFILE, gp);
			return this;
		}

		public <GP> ApiBuilder localModelApi(BiFunction<UUID, String, GP> newGP) {
			api.client.gameProfileFactory = newGP;
			return this;
		}

		public void init() {
			api.initClient();
		}
	}

	private class PlayerRendererImpl<M, RL, RT, MBS, GP> implements PlayerRenderer<M, RL, RT, MBS, GP> {
		private M model;
		private List<M> subModels = new ArrayList<>();
		private Function<RL, RT> renderTypeFactory;
		private AnimationState animState = new AnimationState();
		private RL defaultTexture;
		private GP gameProfile;
		private boolean setupTexture = classes.get(Clazz.RESOURCE_LOCATION) != Void.class;
		private ModelDefinition def;
		private Map<M, RL> textureMap = new HashMap<>();
		private LocalModel localModel;
		private String activePose, activeGesture;

		@Override
		public void setRenderModel(M model) {
			this.model = model;
		}

		@Override
		public void setRenderType(Function<RL, RT> renderTypeFactory) {
			this.renderTypeFactory = renderTypeFactory;
		}

		@Override
		public RT getDefaultRenderType() {
			return renderTypeFactory.apply(defaultTexture);
		}

		@Override
		public RL getDefaultTexture() {
			return defaultTexture;
		}

		@Override
		public AnimationState getAnimationState() {
			return animState;
		}

		@SuppressWarnings("unchecked")
		@Override
		public void preRender(MBS buffers, AnimationMode renderMode) {
			String unique;
			if(localModel != null) {
				localModel.startRender();
				unique = localModel.getUniqueKey();
			} else unique = "api";
			ModelRenderManager<MBS, ?, ?, M> mngr = (ModelRenderManager<MBS, ?, ?, M>) MinecraftClientAccess.get().getPlayerRenderManager();
			Player<?> profile = MinecraftClientAccess.get().getDefinitionLoader().loadPlayer(gameProfile, unique);
			if(profile == null)return;
			def = profile.getModelDefinition();
			if(def != null) {
				profile.animState = animState;
				animState.encodedState = 0;
				CustomPose pose = def.getAnimations().getCustomPoses().get(activePose);
				Gesture gesture = def.getAnimations().getGestures().get(activeGesture);
				if(gesture != null) {
					animState.encodedState = def.getAnimations().getEncoded(gesture);
				}
				if(pose != null) {
					profile.currentPose = pose;
				} else {
					profile.prevPose = null;
				}
				def.itemTransforms.clear();
				mngr.bindModel(model, "api", buffers, def, profile, renderMode);
				mngr.getAnimationEngine().handleAnimation(profile, renderMode);
				if(setupTexture) {
					TextureHandler<RL, RT> tex = ((TextureHandlerFactory<RL, RT>) textureHandlerFactory).apply(null, renderTypeFactory);
					((ModelRenderManager<MBS, TextureHandler<RL, RT>, ?, M>)mngr).bindSkin(model, tex, TextureSheetType.SKIN);
					defaultTexture = tex.getTexture();
				} else {
					mngr.bindSkin(model, null, TextureSheetType.SKIN);
				}
			}
		}

		@SuppressWarnings("unchecked")
		private void prepareSubModel(M model, String arg, RL tex, TextureSheetType sheet) {
			if(def == null)return;
			ModelRenderManager<MBS, ?, ?, M> mngr = (ModelRenderManager<MBS, ?, ?, M>) MinecraftClientAccess.get().getPlayerRenderManager();
			mngr.bindSubModel(this.model, model, arg);
			subModels.add(model);
			if(setupTexture && tex != null) {
				TextureHandler<RL, RT> texH = ((TextureHandlerFactory<RL, RT>) textureHandlerFactory).apply(tex, renderTypeFactory);
				((ModelRenderManager<MBS, TextureHandler<RL, RT>, ?, M>)mngr).bindSkin(model, texH, sheet);
				textureMap.put(model, texH.getTexture());
			} else {
				mngr.bindSkin(model, null, sheet);
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public void postRender() {
			ModelRenderManager<MBS, ?, ?, M> mngr = (ModelRenderManager<MBS, ?, ?, M>) MinecraftClientAccess.get().getPlayerRenderManager();
			mngr.flushBatch(model, "api");
			mngr.unbindModel(model);
			subModels.forEach(mngr::unbindModel);
			subModels.clear();
			textureMap.clear();
			model = null;
			def = null;
			defaultTexture = null;
		}

		@Override
		public void setGameProfile(GP profile) {
			this.gameProfile = profile;
			this.localModel = null;
		}

		@Override
		public void prepareSubModel(M model, SubModelType type, RL tex) {
			String arg = null;
			TextureSheetType sheet = TextureSheetType.SKIN;
			switch (type) {
			case ARMOR_INNER:
				arg = "armor2";
				sheet = TextureSheetType.ARMOR2;
				break;
			case ARMOR_OUTER:
				arg = "armor1";
				sheet = TextureSheetType.ARMOR1;
				break;
			case ELYTRA:
				sheet = TextureSheetType.ELYTRA;
				break;
			case CAPE:
				sheet = TextureSheetType.CAPE;
				break;
			default:
				break;
			}
			prepareSubModel(model, arg, tex, sheet);
		}

		@Override
		public RT getRenderTypeForSubModel(M model) {
			return renderTypeFactory.apply(textureMap.get(model));
		}

		@Override
		public void setLocalModel(LocalModel model) {
			setGameProfile(model.getGameProfile());
			this.localModel = model;
		}

		@Override
		public void setActivePose(String pose) {
			activePose = pose;
		}

		@Override
		public void setActiveGesture(String gesture) {
			activeGesture = gesture;
		}
	}

	public List<ToFloatFunction<Object>> getVoiceProviders() {
		return voice;
	}

	@Override
	public <HM, RL, RT, MBS, GP> PlayerRenderer<HM, RL, RT, MBS, GP> createPlayerRenderer(Class<HM> humanoidModelClass,
			Class<RL> resourceLocationClass, Class<RT> renderTypeClass, Class<MBS> multiBufferSourceClass, Class<GP> gameProfileClass) {
		if(checkClass(humanoidModelClass, Clazz.MODEL))return null;
		if(checkClass(resourceLocationClass, Clazz.RESOURCE_LOCATION))return null;
		if(checkClass(renderTypeClass, Clazz.RENDER_TYPE))return null;
		if(checkClass(multiBufferSourceClass, Clazz.MULTI_BUFFER_SOURCE))return null;
		if(checkClass(gameProfileClass, Clazz.GAME_PROFILE))return null;
		return new PlayerRendererImpl<>();
	}

	public static interface TextureHandlerFactory<RL, RT> {
		TextureHandler<RL, RT> apply(RL tex, Function<RL, RT> func);
	}

	@Override
	public LocalModel loadModel(String name, InputStream is) throws IOException {
		ModelFile file = ModelFile.load(name, is);
		Object gp = gameProfileFactory.apply(UUID.randomUUID(), name);
		return new LocalModelImpl(file, gp);
	}

	private class LocalModelImpl implements LocalModel {
		private ModelFile file;
		private Object gameProfile;
		private String unique;

		public LocalModelImpl(ModelFile file, Object gp) {
			this.file = file;
			this.gameProfile = gp;
			this.unique = "model:" + Base64.getEncoder().encodeToString(file.getDataBlock());
		}

		@SuppressWarnings("unchecked")
		@Override
		public <GP> GP getGameProfile() {
			return (GP) gameProfile;
		}

		@Override
		public void startRender() {
			file.registerLocalCache(MinecraftClientAccess.get().getDefinitionLoader());
		}

		@Override
		public String getUniqueKey() {
			return unique;
		}
	}

	@Override
	public void registerEditorGenerator(String name, String tooltip, Consumer<EditorGui> func) {
		String modid = initingPlugin != null ? initingPlugin.getOwnerModId() : "?";
		Generators.generators.add(new Generators(name, new FormatText("tooltip.cpm.pluginGenerator", new FormatText(tooltip), modid), func));
	}
}
