/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.scte;

import java.time.*;
import java.util.concurrent.atomic.AtomicLong;

import com.wowza.wms.amf.*;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.*;
import com.wowza.wms.media.mp3.model.idtags.*;
import com.wowza.wms.stream.IMediaStream;

public class LiveStreamPacketizerCupertinoDataHandlerDateRange extends LiveStreamPacketizerCupertinoDataHandler
{
	private static final Class<LiveStreamPacketizerCupertinoDataHandlerDateRange> CLASS = LiveStreamPacketizerCupertinoDataHandlerDateRange.class;
	private static final String CLASSNAME = CLASS.getSimpleName();

	private final AtomicLong currentEventId = new AtomicLong();

	private boolean includeEndTag = false;
	private boolean includeEndDateInEndTag = true;
	private boolean includeStartDateInEndTag = true;
	private boolean includeSCTEData = false;

	public LiveStreamPacketizerCupertinoDataHandlerDateRange(LiveStreamPacketizerCupertino liveStreamPacketizer, IMediaStream stream)
	{
		super(liveStreamPacketizer, stream);
		includeEndTag = liveStreamPacketizer.getProperties().getPropertyBoolean("dateRangeIncludeEndTag", includeEndTag);
		includeEndDateInEndTag = liveStreamPacketizer.getProperties().getPropertyBoolean("dateRangeIncludeEndDateInEndTag", includeEndDateInEndTag);
		includeStartDateInEndTag = liveStreamPacketizer.getProperties().getPropertyBoolean("dateRangeIncludeStartDateInEndTag", includeStartDateInEndTag);
		includeSCTEData = liveStreamPacketizer.getProperties().getPropertyBoolean("dateRangeIncludeSCTEData", includeSCTEData);
	}

	@Override
	public void onFillChunkDataPacket(LiveStreamPacketizerCupertinoChunk chunk, CupertinoPacketHolder holder, AMFPacket packet, ID3Frames id3Frames)
	{
		int rendition = chunk.getRendition().getRendition();
		extractSCTEData(packet, rendition).ifPresent(data -> {
			AMFDataObj commandObj = data.getObject("command");
			String command = commandObj.getString("SpliceCommand");
			if(command.equalsIgnoreCase("insert"))
			{
				AMFDataObj eventObj = commandObj.getObject("event");
				long eventId = eventObj.getLong("eventID");
				boolean spliceOut = eventObj.getBoolean("outOfNetwork");
				if (spliceOut) 
				{
					events.computeIfAbsent(eventId, id -> {
						currentEventId.set(id);
						AMFDataObj spliceTimeObj = eventObj.getObject("spliceTime");
						long spliceTimecode = spliceTimeObj.getLong("spliceTimeMS");
						boolean durationFlag = eventObj.getBoolean("durationFlag");
						long breakDuration = durationFlag ? (long) (eventObj.getDouble("breakDuration") / 90000) : 0L;
						OnDataEvent newEvent = new OnDataEvent(id);
						newEvent.startTime = spliceTimecode;
						newEvent.duration = breakDuration;
						newEvent.spliceOutData = getRawDataAsHexStr(data);
						checkAdjustChunkEnd(newEvent.startTime);
						return newEvent;
					});
				}
				else // spliceIn
				{
					events.computeIfPresent(currentEventId.get(), (id, event) -> {
						AMFDataObj spliceTimeObj = eventObj.getObject("spliceTime");
						long spliceTimecode = spliceTimeObj.getLong("spliceTimeMS");
						long breakDuration =  spliceTimecode - event.startTime;
						if (breakDuration > 0) 
							event.duration = breakDuration;
						
						event.spliceInData = getRawDataAsHexStr(data);
						checkAdjustChunkEnd(event.startTime + event.duration);
						return event;
					});
				}					
			}
		});
	}

	@Override
	public void onFillChunkEnd(LiveStreamPacketizerCupertinoChunk chunk, long timecode) {

		CupertinoUserManifestHeaders chunkHeaders = chunk.getUserManifestHeaders();
		events.forEach((id, event) -> { 
			String idString = String.format("ID=\"%d\"", event.eventId);
			String startString = ",START-DATE=\"" + dateTimeFormatter.format(Instant.ofEpochMilli(streamStartTime + event.startTime)) + "\"";
            // first chunk for event
            if (event.startTime >= chunk.getStartTimecode() && event.startTime < timecode) 
			{
				String durationString = event.duration == 0 ? "" : ((includeEndTag ? ",PLANNED-" : ",") + String.format("DURATION=%.3f", event.duration / 1000f));
				String dateRangeString = "EXT-X-DATERANGE:" + idString + startString + durationString;
				if(includeSCTEData && event.spliceOutData != null)
					dateRangeString += ",SCTE35-OUT=" + event.spliceOutData;
				
				chunkHeaders.addHeader(dateRangeString);
			}
			// first chunk after event finishes
			else if ((event.startTime + event.duration >= chunk.getStartTimecode()) && (event.startTime + event.duration < timecode)) 
			{
				if (includeEndTag)
				{
					String dateString = "";
					if(includeStartDateInEndTag)
						dateString = startString;
					
					if(includeEndDateInEndTag)
						dateString += ",END-DATE=\"" + dateTimeFormatter.format(Instant.ofEpochMilli(streamStartTime + event.startTime + event.duration)) + "\"";
					
					String durationString = event.duration == 0 ? "" : String.format(",DURATION=%.3f", event.duration / 1000f);
					String dateRangeString = "EXT-X-DATERANGE:" + idString + dateString + durationString;
	
					if(includeSCTEData && event.spliceInData != null)
						dateRangeString += ",SCTE35-IN=" + event.spliceInData;
						
					chunkHeaders.addHeader(dateRangeString);
				}

				event.expired = true;
			}
		});
	}

}
