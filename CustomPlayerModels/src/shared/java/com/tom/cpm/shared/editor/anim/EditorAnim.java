package com.tom.cpm.shared.editor.anim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.tom.cpl.math.Vec3f;
import com.tom.cpm.shared.animation.CustomPose;
import com.tom.cpm.shared.animation.Gesture;
import com.tom.cpm.shared.animation.IPose;
import com.tom.cpm.shared.animation.InterpolatorChannel;
import com.tom.cpm.shared.animation.VanillaPose;
import com.tom.cpm.shared.animation.interpolator.Interpolator;
import com.tom.cpm.shared.animation.interpolator.InterpolatorType;
import com.tom.cpm.shared.editor.Editor;
import com.tom.cpm.shared.editor.ElementType;
import com.tom.cpm.shared.editor.ModelElement;
import com.tom.cpm.shared.editor.actions.ActionBuilder;
import com.tom.cpm.shared.editor.gui.AnimPanel.IAnim;
import com.tom.cpm.shared.editor.tree.TreeElement.VecType;
import com.tom.cpm.shared.model.PartValues;
import com.tom.cpm.shared.model.render.VanillaModelPart;

public class EditorAnim implements IAnim {
	private List<ModelElement> components;
	private final List<AnimFrame> frames = new ArrayList<>();
	public int duration = 1000;
	private Interpolator[][] psfs;
	private AnimFrame currentFrame;
	public final Editor editor;
	public String filename;
	public AnimationType type;
	public IPose pose;
	public String displayName;
	public boolean add = true;
	public boolean loop;
	public int priority;
	public InterpolatorType intType = InterpolatorType.POLY_LOOP;
	public float layerDefault;

	public EditorAnim(Editor e, String filename, AnimationType type, boolean initNew) {
		this.editor = e;
		this.filename = filename;
		this.type = type;
		if(initNew)addFrame();
	}

	private void calculateSplines() {
		components = frames.stream().flatMap(AnimFrame::getAllElementsFiltered).distinct().collect(Collectors.toList());

		psfs = new Interpolator[components.size()][InterpolatorChannel.VALUES.length];

		for (int component = 0; component < components.size(); component++) {
			for (InterpolatorChannel channel : InterpolatorChannel.VALUES) {
				Interpolator i = intType.create();
				i.init(AnimFrame.toArray(this, components.get(component), channel), channel);
				psfs[component][channel.channelID()] = i;
			}
		}
	}

	public float getValue(ModelElement component, InterpolatorChannel attribute, double time) {
		return (float) psfs[components.indexOf(component)][attribute.channelID()].applyAsDouble(time);
	}

	public void apply() {
		if(currentFrame != null) {
			currentFrame.apply();
		}
	}

	public void applyPlay(long millis) {
		if(components == null || psfs == null)calculateSplines();
		float step;
		boolean remap = false;
		if(pose != null && pose instanceof VanillaPose && ((VanillaPose)pose).hasStateGetter()) {
			step = editor.animTestSliders.getOrDefault("__pose", 0f);
			remap = true;
		} else if(displayName.startsWith(Gesture.VALUE_LAYER_PREFIX)) {
			step = editor.animTestSliders.getOrDefault(getId(), layerDefault);
			remap = true;
		} else step = (float) millis % duration / duration * frames.size();

		if(remap) {
			int dd = (int) (step * VanillaPose.DYNAMIC_DURATION_MUL);
			step = (float) dd % VanillaPose.DYNAMIC_DURATION_DIV / VanillaPose.DYNAMIC_DURATION_DIV * frames.size();
		}

		for (int i = 0; i < components.size(); i++) {
			ModelElement component = components.get(i);
			component.rc.setRotation(add,
					getValue(component, InterpolatorChannel.ROT_X, step),
					getValue(component, InterpolatorChannel.ROT_Y, step),
					getValue(component, InterpolatorChannel.ROT_Z, step)
					);
			component.rc.setPosition(add,
					getValue(component, InterpolatorChannel.POS_X, step),
					getValue(component, InterpolatorChannel.POS_Y, step),
					getValue(component, InterpolatorChannel.POS_Z, step));
			component.rc.setColor(
					getValue(component, InterpolatorChannel.COLOR_R, step),
					getValue(component, InterpolatorChannel.COLOR_G, step),
					getValue(component, InterpolatorChannel.COLOR_B, step));
			component.rc.setVisible(frames.get((int) step).getVisible(component));
			component.rc.setRenderScale(add,
					getValue(component, InterpolatorChannel.SCALE_X, step),
					getValue(component, InterpolatorChannel.SCALE_Y, step),
					getValue(component, InterpolatorChannel.SCALE_Z, step)
					);
		}
	}

	public void setPosition(Vec3f v) {
		if(currentFrame != null && editor.getSelectedElement() != null) {
			currentFrame.setPos(editor.getSelectedElement(), v);
		}
		components = null;
		psfs = null;
	}

	public void setRotation(Vec3f v) {
		if(currentFrame != null && editor.getSelectedElement() != null) {
			currentFrame.setRot(editor.getSelectedElement(), v);
		}
		components = null;
		psfs = null;
	}

	public void setScale(Vec3f v) {
		if(currentFrame != null && editor.getSelectedElement() != null) {
			currentFrame.setScale(editor.getSelectedElement(), v);
		}
		components = null;
		psfs = null;
	}

	public void switchVisible() {
		if(currentFrame != null && editor.getSelectedElement() != null) {
			currentFrame.switchVis(editor.getSelectedElement());
		}
		components = null;
		psfs = null;
	}

	public void clearSelectedData(boolean all) {
		if(currentFrame != null && editor.getSelectedElement() != null) {
			ActionBuilder ab = editor.action("clearAnim");
			if(all) {
				frames.forEach(f -> f.clearSelectedData(ab, editor.getSelectedElement()));
			} else
				currentFrame.clearSelectedData(ab, editor.getSelectedElement());
			ab.execute();
		}
		components = null;
		psfs = null;
	}

	public void setColor(int rgb) {
		if(currentFrame != null && editor.getSelectedElement() != null) {
			currentFrame.setColor(editor.getSelectedElement(), rgb);
		}
		components = null;
		psfs = null;
	}

	public void addFrame() {
		AnimFrame frm = new AnimFrame(this);
		editor.action("add", "action.cpm.animFrame").addToList(frames, frm).onAction(this::clearCache).execute();
		if(currentFrame != null)frm.copy(currentFrame);
		currentFrame = frm;
	}

	public void deleteFrame() {
		if(currentFrame != null) {
			int ind = frames.indexOf(currentFrame) - 1 + frames.size();
			editor.action("remove", "action.cpm.animFrame").removeFromList(frames, currentFrame).onAction(this::clearCache).execute();
			if(!frames.isEmpty())currentFrame = frames.get(ind % frames.size());
		}
	}

	public List<AnimFrame> getFrames() {
		return frames;
	}

	public List<ModelElement> getComponentsFiltered() {
		return frames.stream().flatMap(AnimFrame::getAllElementsFiltered).distinct().collect(Collectors.toList());
	}

	@Override
	public String toString() {
		if(pose != null)return editor.gui().i18nFormat("label.cpm.anim_pose", pose.getName(editor.gui(), getDisplayName()));
		else if(isLayer())return editor.gui().i18nFormat("label.cpm.anim_layer", getDisplayName());
		return editor.gui().i18nFormat("label.cpm.anim_gesture", getDisplayName());
	}

	public AnimFrame getSelectedFrame() {
		return currentFrame;
	}

	public void setSelectedFrame(AnimFrame currentFrame) {
		this.currentFrame = currentFrame;
	}

	public void prevFrame() {
		if(currentFrame == null && !frames.isEmpty())currentFrame = frames.get(0);
		if(frames.size() > 1) {
			int ind = frames.indexOf(currentFrame) - 1 + frames.size();
			currentFrame = frames.get(ind % frames.size());
		}
	}

	public void nextFrame() {
		if(currentFrame == null && !frames.isEmpty())currentFrame = frames.get(0);
		if(frames.size() > 1) {
			int ind = frames.indexOf(currentFrame) + 1;
			currentFrame = frames.get(ind % frames.size());
		}
	}

	public boolean isCustom () {
		return type == AnimationType.GESTURE || pose instanceof CustomPose;
	}

	public void clearCache() {
		components = null;
		psfs = null;
	}

	public void moveFrame(int i) {
		if(currentFrame == null || frames.size() < 2)return;
		int ind = frames.indexOf(currentFrame);
		if(ind == -1)return;
		int newInd = ind + i;
		if(newInd < 0 || newInd > frames.size())return;
		ActionBuilder ab = editor.action("move", "action.cpm.animFrame");
		Map<AnimFrame, Float> map = new HashMap<>();
		for (int j = 0; j < frames.size(); j++) {
			map.put(frames.get(j), (float) j);
		}
		ab.addToMap(map, currentFrame, newInd + 0.1f * i);
		ab.onAction(() -> frames.sort(Comparator.comparing(map::get)));
		ab.onAction(this::clearCache);
		ab.execute();
	}

	public float getAnimProgess() {
		int ind = frames.indexOf(currentFrame);
		if(ind == -1)return 0;
		if(frames.size() == 1)return 1;
		return ind / (float) (frames.size() - 1);
	}

	@Override
	public IPose getPose() {
		return pose;
	}

	public String getId() {
		String nm = displayName;
		int i = nm.indexOf('#');
		if(i == -1)return nm;
		if(i == 0)return "";
		return nm.substring(0, i);
	}

	public String getDisplayGroup() {
		String nm = getDisplayName();
		int i = nm.indexOf('#');
		if(i == -1)return nm;
		if(i == 0)return "";
		nm = nm.substring(0, i);
		if(pose != null)return editor.gui().i18nFormat("label.cpm.anim_pose", pose.getName(editor.gui(), nm));
		else if(isLayer())return editor.gui().i18nFormat("label.cpm.anim_layer", nm);
		return editor.gui().i18nFormat("label.cpm.anim_gesture", nm);
	}

	public void updateGui() {
		AnimFrame selFrm = this.getSelectedFrame();
		if(selFrm != null) {
			ModelElement selectedElement = editor.getSelectedElement();
			editor.setAnimFrame.accept(this.getFrames().indexOf(selFrm));
			if(selectedElement != null) {
				IElem dt = selFrm.getData(selectedElement);
				if(dt == null) {
					if(this.add) {
						editor.setAnimPos.accept(new Vec3f());
						editor.setAnimRot.accept(new Vec3f());
						if(selectedElement.type != ElementType.ROOT_PART)
							editor.setAnimScale.accept(new Vec3f(1, 1, 1));
					} else if(selectedElement.type == ElementType.ROOT_PART){
						PartValues val = ((VanillaModelPart)selectedElement.typeData).getDefaultSize(editor.skinType);
						editor.setAnimPos.accept(val.getPos().add(selectedElement.pos));
						editor.setAnimRot.accept(selectedElement.rotation);
						editor.setAnimScale.accept(null);
					} else {
						editor.setAnimPos.accept(selectedElement.pos);
						editor.setAnimRot.accept(selectedElement.rotation);
						editor.setAnimScale.accept(new Vec3f(1, 1, 1));
					}
					if(!selectedElement.texture || selectedElement.recolor) {
						editor.setAnimColor.accept(selectedElement.rgb);
					}
					if(selectedElement.type != ElementType.ROOT_PART)
						editor.setAnimShow.accept(!selectedElement.hidden);
				} else {
					if(!selectedElement.texture || selectedElement.recolor) {
						Vec3f c = dt.getColor();
						editor.setAnimColor.accept((((int) c.x) << 16) | (((int) c.y) << 8) | ((int) c.z));
					}
					editor.setAnimPos.accept(dt.getPosition());
					editor.setAnimRot.accept(dt.getRotation());
					if(selectedElement.type != ElementType.ROOT_PART) {
						editor.setAnimScale.accept(dt.getScale());
						editor.setAnimShow.accept(dt.isVisible());
					}
				}
			}
			editor.setFrameDelEn.accept(true);
		}
		editor.setFrameAddEn.accept(true);
		editor.setAnimDelEn.accept(true);
		if(!(this.pose instanceof VanillaPose && ((VanillaPose)this.pose).hasStateGetter()))
			editor.setAnimDuration.accept(this.duration);
		editor.setAnimPlayEn.accept(this.getFrames().size() > 1);
		editor.setAnimPriority.accept(this.priority);
	}

	public void beginDrag() {
		ModelElement sel = editor.getSelectedElement();
		if(currentFrame != null && sel != null)currentFrame.beginDrag(sel);
	}

	public void endDrag() {
		if(currentFrame != null)currentFrame.endDrag();
	}

	public void dragVal(VecType type, Vec3f vec) {
		if(currentFrame != null)currentFrame.dragVal(type, vec);
	}

	public String getDisplayName() {
		if(displayName.startsWith(Gesture.LAYER_PREFIX))return displayName.substring(Gesture.LAYER_PREFIX.length());
		if(displayName.startsWith(Gesture.VALUE_LAYER_PREFIX))return displayName.substring(Gesture.VALUE_LAYER_PREFIX.length());
		return displayName;
	}

	public boolean isLayer() {
		return displayName.startsWith(Gesture.LAYER_PREFIX) || displayName.startsWith(Gesture.VALUE_LAYER_PREFIX);
	}
}
