/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.scte;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertino;
import com.wowza.wms.httpstreamer.mpegdashstreaming.livestreampacketizer.LiveStreamPacketizerMPEGDash;
import com.wowza.wms.logging.*;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.livepacketizer.*;

public class LiveStreamPacketizerListener extends LiveStreamPacketizerActionNotifyBase
{
	private enum HlsTagType { DATERANGE, CUE }
	private static final Class<LiveStreamPacketizerListener> CLASS = LiveStreamPacketizerListener.class;
	private static final String CLASSNAME = CLASS.getSimpleName();
	private final IApplicationInstance appInstance;
	private final WMSLogger logger;

	private HlsTagType hlsTagType = HlsTagType.DATERANGE;

	public LiveStreamPacketizerListener(IApplicationInstance appInstance)
	{
		this.appInstance = appInstance;
		this.logger = WMSLoggerFactory.getLoggerObj(CLASS, appInstance);
		try
		{
			hlsTagType = HlsTagType.valueOf(appInstance.getLiveStreamPacketizerProperties().getPropertyStr("scteAdsHlsTagType", "DATERANGE"));
		}
		catch (Exception e)
		{
			logger.error(String.format("%s.onAppStart [%s] exception: %s", CLASSNAME, appInstance.getContextStr(), e));
		}
	}

	@Override
	public void onLiveStreamPacketizerInit(ILiveStreamPacketizer liveStreamPacketizer, String streamName)
	{
		try
		{
			IMediaStream stream = appInstance.getStreams().getStream(streamName);
			if (!liveStreamPacketizer.isRepeaterEdge() && liveStreamPacketizer instanceof LiveStreamPacketizerCupertino)
			{
				LiveStreamPacketizerCupertinoDataHandler dataHandler = hlsTagType == HlsTagType.CUE
						? new LiveStreamPacketizerCupertinoDataHandlerCue((LiveStreamPacketizerCupertino) liveStreamPacketizer, stream)
						: new LiveStreamPacketizerCupertinoDataHandlerDateRange((LiveStreamPacketizerCupertino) liveStreamPacketizer, stream);
				((LiveStreamPacketizerCupertino)liveStreamPacketizer).setDataHandler(dataHandler);
			}
			else if (liveStreamPacketizer instanceof LiveStreamPacketizerMPEGDash)
			{
				((LiveStreamPacketizerMPEGDash)liveStreamPacketizer).setDataHandler(new LiveStreamPacketizerMpegDashDataHandler((LiveStreamPacketizerMPEGDash) liveStreamPacketizer, stream));
			}
		}
		catch (Exception e)
		{
			logger.warn(CLASSNAME + ".onLiveStreamPacketizerCreate cannot set LiveStreamPacketizerDataHandler. " + e.getMessage());
		}
	}

}
