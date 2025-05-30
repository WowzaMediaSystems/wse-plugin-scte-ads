/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.scte;

import com.wowza.wms.application.*;
import com.wowza.wms.logging.*;
import com.wowza.wms.module.*;

public class ModuleAdInsertion extends ModuleBase
{
	private static final Class<ModuleAdInsertion> CLASS = ModuleAdInsertion.class;

	public static final String MODULE_NAME = CLASS.getSimpleName();
	public static final String MODULE_VERSION = ReleaseInfo.getVersion();

	private WMSLogger logger;

	public void onAppStart(IApplicationInstance appInstance)
	{
		logger = WMSLoggerFactory.getLoggerObj(appInstance);
		logger.info(MODULE_NAME + ".onAppStart: [" + appInstance.getContextStr() + "] " + ReleaseInfo.getProject() + " version: " + MODULE_VERSION + " build: " + ReleaseInfo.getBuildNumber());

		appInstance.addLiveStreamPacketizerListener(new LiveStreamPacketizerListener(appInstance));
	}

	public void onAppStop(IApplicationInstance appInstance)
	{
		logger.info(MODULE_NAME + ".onAppStop: [" + appInstance.getContextStr() + "]");
	}
}
