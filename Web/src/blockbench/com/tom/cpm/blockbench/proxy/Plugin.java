package com.tom.cpm.blockbench.proxy;

import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;

@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "$$ugwt_m_Plugin_$$")
public class Plugin {

	public static native void register(String id, PluginProperties pr);

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "$$ugwt_m_Object_$$")
	public static class PluginProperties {
		public String name, author, description, icon, version, variant;
		public String[] tags;
		public Callback onload, onunload;
	}

	@JsFunction
	public interface Callback {
		void run();
	}
}
