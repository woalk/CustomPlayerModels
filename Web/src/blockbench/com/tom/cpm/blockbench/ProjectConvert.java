package com.tom.cpm.blockbench;

import com.tom.cpm.blockbench.proxy.Blockbench;
import com.tom.cpm.blockbench.proxy.Blockbench.ExportProperties;
import com.tom.cpm.blockbench.proxy.Blockbench.ImportProperties;
import com.tom.cpm.blockbench.proxy.Dialog;
import com.tom.cpm.blockbench.proxy.Global;
import com.tom.cpm.blockbench.proxy.Project;
import com.tom.cpm.shared.editor.Editor;
import com.tom.cpm.shared.editor.project.ProjectIO;
import com.tom.cpm.shared.editor.util.StoreIDGen;
import com.tom.cpm.web.client.util.JSZip;

import elemental2.core.ArrayBuffer;
import elemental2.core.JsObject;
import elemental2.dom.Blob;
import elemental2.dom.DomGlobal;
import elemental2.dom.Event;
import elemental2.promise.Promise;
import jsinterop.base.Js;
import jsinterop.base.JsPropertyMap;

public class ProjectConvert {
	private static final String CPM_DT_GROUP = "CPM_data_DO_NOT_EDIT";
	private static Dialog errorDialog, warnDialog;

	public static Promise<Blob> compile(JsObject options) {
		DomGlobal.console.log("Export");
		Editor editor = new Editor();
		editor.setGui(BBGui.makeFrame());
		editor.loadDefaultPlayerModel();
		try {
			return new BlockbenchExport(editor).doExport().then(__ -> {
				try {
					StoreIDGen storeIDgen = new StoreIDGen();
					Editor.walkElements(editor.elements, storeIDgen::setID);
					ProjectIO.saveProject(editor, editor.project);
					return editor.project.save().then(model -> {
						JsPropertyMap<?> map = JsPropertyMap.of("model", model, "options", options);
						PluginStart.codec.dispatchEvent("compile", Js.cast(map));
						Blob m = Js.cast(map.get("model"));
						return Promise.resolve(m);
					});
				} catch (Throwable e) {
					return Promise.reject(e);
				}
			});
		} catch (Throwable e) {
			return Promise.reject(e);
		}
	}

	public static void parse(ArrayBuffer ab) {
		DomGlobal.console.log("Parse");
		PluginStart.codec.dispatchEvent("parse", Js.cast(JsPropertyMap.of("arraybuffer", ab)));
		Editor editor = new Editor();
		editor.setGui(BBGui.makeFrame());
		editor.loadDefaultPlayerModel();
		new JSZip().loadAsync(ab).then(editor.project::load).then(__ -> {
			try {
				ProjectIO.loadProject(editor, editor.project);
			} catch (Exception e) {
				System.out.println("Err:" + e);
				return Promise.reject(e);
			}
			return Promise.resolve((Void) null);
		}).then(v -> {
			new BlockbenchImport(editor).doImport();
			return null;
		}).catch_(err -> {
			if(err instanceof Throwable)
				((Throwable) err).printStackTrace();
			else
				DomGlobal.console.log(err);
			errorDialog.lines = new String[] {"Error while importing:<br>", err.toString(), "<br>Please report this on GitHub"};
			errorDialog.show();
			return null;
		});
	}

	public static float ceil(float val) {
		return (float) Math.ceil(val - 0.01);
	}

	public static float floor(float val) {
		return (float) Math.floor(val + 0.01);
	}

	public static void open(Event event) {
		ImportProperties pr = new ImportProperties();
		pr.extensions = new String[] {"cpmproject"};
		pr.type = "Customizable Player Models Project";
		pr.readtype = "binary";
		pr.resource_id = "cpmproject_files";
		Blockbench.import_(pr, files -> {
			Global.newProject(PluginStart.format);
			try {
				parse(files[0].binaryContent);
			} catch (Throwable e) {
				e.printStackTrace();
				errorDialog.lines = new String[] {"Error while importing:<br>", e.toString(), "<br>Please report this on GitHub"};
				errorDialog.show();
			}
			String csname = files[0].name.replace(".cpmproject", "").replaceAll("\\s+", "_").toLowerCase();
			Project.name = csname;
			Project.geometry_name = csname;
		});
	}

	public static void export() {
		try {
			compile(null).then(content -> {
				ExportProperties pr = new ExportProperties();
				pr.resource_id = "model";
				pr.type = PluginStart.codec.name;
				pr.extensions = new String[] {PluginStart.codec.extension};
				pr.name = PluginStart.codec.fileName();
				pr.startpath = PluginStart.codec.startPath();
				pr.binaryContent = content;
				pr.custom_writer = Global.isApp() ? PluginStart.codec::write : null;
				Blockbench.export(pr, PluginStart.codec::afterDownload);
				return null;
			}).catch_(err -> {
				if(err instanceof Throwable)
					((Throwable) err).printStackTrace();
				else
					DomGlobal.console.log(err);
				errorDialog.lines = new String[] {"Error while importing:<br>", err.toString(), "<br>Please report this on GitHub"};
				errorDialog.show();
				return null;
			});
		} catch (Exception e) {
			e.printStackTrace();
			errorDialog.lines = new String[] {"Error while importing:<br>", e.toString(), "<br>Please report this on GitHub"};
			errorDialog.show();
		}
	}

	public static void initDialogs() {
		Dialog.DialogProperties dctr = new Dialog.DialogProperties();
		dctr.id = "cpm_error";
		dctr.title = "Error in CPM Plugin";
		dctr.lines = new String[] {"?"};
		dctr.singleButton = false;
		errorDialog = new Dialog(dctr);

		dctr = new Dialog.DialogProperties();
		dctr.id = "cpm_warn";
		dctr.title = "Warning";
		dctr.lines = new String[] {"?"};
		warnDialog = new Dialog(dctr);
	}
}
