package de.persosim.remoteifd.ui;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;
import org.globaltester.logging.tags.LogTag;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import de.persosim.driver.connector.CommManager;
import de.persosim.driver.connector.CommProvider;
import de.persosim.simulator.log.PersoSimLogTags;
import de.persosim.simulator.preferences.EclipsePreferenceAccessor;
import de.persosim.simulator.preferences.PersoSimPreferenceManager;
import de.persosim.websocket.RemoteIfdConfigManager;
import de.persosim.websocket.WebsocketComm;

public class Activator implements BundleActivator {

	private static BundleContext context;
	private static EclipseRemoteIfdConfigManager remoteIfdConfigManager;
	public static final String PLUGIN_ID = "de.persosim.remoteifd.ui";
	private CommProvider provider;

	static BundleContext getContext() {
		return context;
	}


	@Override
	public void start(BundleContext bundleContext) throws Exception {
		BasicLogger.log("START Activator Remote IFD UI", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.SYSTEM_TAG_ID));
		Activator.context = bundleContext;

		provider = type -> {
		    if ("WEBSOCKET".equals(type) || type == null) {
		        return new WebsocketComm(null, new EclipseRemoteIfdConfigManager(PLUGIN_ID));
		    }
		    return null;
		};

		CommManager.addCommProvider(provider);

		PersoSimPreferenceManager.setPreferenceAccessorIfNotAvailable(new EclipsePreferenceAccessor());
		BasicLogger.log("END Activator Remote IFD UI", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.SYSTEM_TAG_ID));
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
