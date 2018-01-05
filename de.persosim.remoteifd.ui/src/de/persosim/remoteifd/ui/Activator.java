package de.persosim.remoteifd.ui;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import de.persosim.websocket.RemoteIfdConfigManager;

public class Activator implements BundleActivator {

	private static BundleContext context;
	private static EclipseRemoteIfdConfigManager remoteIfdConfigManager;
	public static String PLUGIN_ID = "de.persosim.remoteifd.ui";

	static BundleContext getContext() {
		return context;
	}
	
	@Override
	public void start(BundleContext bundleContext) throws Exception {
		Activator.context = bundleContext;
	}
	
	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		Activator.context = null;
	}
	
	/**
	 * @return the application wide remote ifd configuration
	 */
	public static RemoteIfdConfigManager getRemoteIfdConfig() {
		if (remoteIfdConfigManager == null) {
			remoteIfdConfigManager = new EclipseRemoteIfdConfigManager(PLUGIN_ID);
		}
		return remoteIfdConfigManager;
	}

}
