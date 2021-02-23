package de.persosim.remoteifd.ui;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import de.persosim.driver.connector.CommManager;
import de.persosim.driver.connector.CommProvider;
import de.persosim.driver.connector.IfdComm;
import de.persosim.simulator.preferences.EclipsePreferenceAccessor;
import de.persosim.simulator.preferences.PersoSimPreferenceManager;
import de.persosim.websocket.RemoteIfdConfigManager;
import de.persosim.websocket.WebsocketComm;

public class Activator implements BundleActivator {

	private static BundleContext context;
	private static EclipseRemoteIfdConfigManager remoteIfdConfigManager;
	public static String PLUGIN_ID = "de.persosim.remoteifd.ui";
	private CommProvider provider;

	static BundleContext getContext() {
		return context;
	}

	
	@Override
	public void start(BundleContext bundleContext) throws Exception {
		Activator.context = bundleContext;
		
		provider = new CommProvider() {
			
			@Override
			public IfdComm get(String type) {
				if ("WEBSOCKET".equals(type) || type == null){
					return new WebsocketComm(null, new EclipseRemoteIfdConfigManager(PLUGIN_ID));
				}
				return null;
			}
		};
		
		PersoSimPreferenceManager.setPreferenceAccessor(new EclipsePreferenceAccessor());
		
		CommManager.addCommProvider(provider);
	}
	
	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		Activator.context = null;

		CommManager.removeCommProvider(provider);
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
