/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.scte;

import com.wowza.wms.amf.*;
import com.wowza.wms.httpstreamer.cmafstreaming.livestreampacketizer.CmafSegment;
import com.wowza.wms.httpstreamer.model.*;
import com.wowza.wms.httpstreamer.mpegdashstreaming.file.*;
import com.wowza.wms.httpstreamer.mpegdashstreaming.livestreampacketizer.*;
import com.wowza.wms.httpstreamer.mpegdashstreaming.util.MPEGDashUtils;
import com.wowza.wms.stream.IMediaStream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LiveStreamPacketizerMpegDashDataHandler extends LiveStreamPacketizerDataHandlerBase implements IHTTPStreamerMPEGDashLivePacketizerDataHandler
{
    private final LiveStreamPacketizerMPEGDash liveStreamPacketizer;

    protected final Map<Long, OnDashDataEvent> events = new ConcurrentHashMap<>();

    public LiveStreamPacketizerMpegDashDataHandler(LiveStreamPacketizerMPEGDash liveStreamPacketizer, IMediaStream stream)
    {
        super(stream);
        this.liveStreamPacketizer = liveStreamPacketizer;
    }

    private boolean isEventExpired(EventStream eventStream, long firstSegmentTimecode, Event event)
    {
        long presentationTimeMsec = MPEGDashUtils.timescaledToMsec(event.getPresentationTime(), eventStream.getTimeScale());
        long durationMsec = event.getDuration() == null ? 0 : MPEGDashUtils.timescaledToMsec(event.getDuration(), eventStream.getTimeScale());
        return presentationTimeMsec + durationMsec < firstSegmentTimecode;
    }

    private long getFirstManifestSegmentTimecode()
    {
        long firstSegmentTimecode = -1;
        CmafSegment firstSegment;
        long idFirst = liveStreamPacketizer.getVideoSegmentIdFirst();
        long idLast = liveStreamPacketizer.getVideoSegmentIdLast();
        boolean hasVideo = true;
        if (idFirst == -1)
        {
            hasVideo = false;
            idFirst = liveStreamPacketizer.getAudioSegmentIdFirst();
            idLast = liveStreamPacketizer.getAudioSegmentIdLast();
        }
        long firstManifestId = Math.max(idFirst, idLast - (liveStreamPacketizer.getPlaylistSegmentCount() - 1));
        if (hasVideo)
            firstSegment = liveStreamPacketizer.getVideoSegment(liveStreamPacketizer.getDefaultSegmentFormat(), firstManifestId);
        else
            firstSegment = liveStreamPacketizer.getAudioSegment(liveStreamPacketizer.getDefaultSegmentFormat(), firstManifestId);
        if (firstSegment != null)
            firstSegmentTimecode = firstSegment.getStartTimeCodeInMilliseconds();
        return firstSegmentTimecode;
    }

    @Override
    public void onFillSegmentDataPacket(LiveStreamPacketizerPacketHolder liveStreamPacketizerPacketHolder, AMFPacket packet, InbandEventStreams inbandEventStreams, EventStream eventStream)
    {
        extractSCTEData(packet, -1).ifPresent(data -> {
            AMFDataObj commandObj = data.getObject("command");
            String command = commandObj.getString("SpliceCommand");
            String rawData = data.getString("rawData");
            if (rawData == null)
                return;
                
            if (command.equalsIgnoreCase("insert"))
            {
                AMFDataObj eventObj = commandObj.getObject("event");
                long eventId = eventObj.getLong("eventID");
                boolean spliceOut = eventObj.getBoolean("outOfNetwork");
                
                if (eventStream.getRegisteredEventStream(eventId) != null)
                    return;

                if (spliceOut)
                {
                    OnDashDataEvent event = events.computeIfAbsent(eventId, id -> {
                        AMFDataObj spliceTimeObj = eventObj.getObject("spliceTime");
                        long spliceTime = spliceTimeObj.getLong("spliceTime");
                        boolean isUTC = spliceTimeObj.getBoolean("isUTC");
                        long presentationTime = -1;
                        if (!isUTC && spliceTimeObj.containsKey("spliceTimeMS"))
                            spliceTime = spliceTimeObj.getLong("spliceTimeMS") * 90;
                            presentationTime = spliceTime;
                        long duration = -1;
                        if (eventObj.getBoolean("durationFlag") && eventObj.containsKey("breakDuration"))
                            duration = (long) eventObj.getDouble("breakDuration");
                        OnDashDataEvent newEvent = new OnDashDataEvent(eventId, rawData, presentationTime, duration);
                        return newEvent;
                    });
                }
            }
        });
    }

    @Override
	public void onFillSegmentStart(long startTimecode, long endTimecode, InbandEventStreams inbandEventStreams, EventStream eventStream)
    {   
        events.entrySet().removeIf(e -> e.getValue().expired);
        events.forEach((id, event) -> {
            checkAdjustChunkEnd(event.presentationTime, startTimecode, endTimecode);
            checkAdjustChunkEnd(event.presentationTime + event.duration, startTimecode, endTimecode);
        });
    }

    protected void addToEventStream(OnDashDataEvent event, EventStream eventStream)
    {
        Event newEvent = new Event(event.data, event.presentationTime);
        newEvent.setId(event.eventId);
        if (event.duration >= 0)
            newEvent.setDuration(event.duration);
        eventStream.registerEvent(newEvent);
    }

    protected void checkAdjustChunkEnd(long timecode, long chunkEnd, long chunkStart)
    {
        if (timecode > chunkStart && timecode < chunkEnd)
        {
            logger.info(String.format("%s.checkAdjustChunkEnd [%s] timecode: %d, chunkStart: %d, chunkEnd: %d", getClass().getSimpleName(), stream.getContextStr(), timecode, chunkStart, chunkEnd));
            liveStreamPacketizer.setSegmentStopKeyTimecode(timecode);
        }
    }
   
    @Override
    public void onFillSegmentEnd(long startTimecode, long endTimecode, InbandEventStreams inbandEventStreams, EventStream eventStream)
    {   
        long firstSegmentTimecode = getFirstManifestSegmentTimecode();
        events.forEach((id, e) -> {
            if (eventStream.getRegisteredEventStream(e.eventId) == null)
                addToEventStream(e, eventStream);

            Event event = eventStream.getRegisteredEventStream(e.eventId);
            if (event != null) 
                e.expired = isEventExpired(eventStream, firstSegmentTimecode, event);
        });

        if (eventStream.getRegisteredEventStreams().isEmpty())
            return;
        
        if (liveStreamPacketizer.getProperties().getPropertyBoolean("scteAdsRemoveExpiredEvents", true))
        {
            if (firstSegmentTimecode == -1)
                return;
            eventStream.getRegisteredEventStreams()
                    .removeIf(event -> isEventExpired(eventStream, firstSegmentTimecode, event));
        }
    }

    protected static class OnDashDataEvent
    {
        final long eventId;
        boolean expired = false;
        long presentationTime;
        long duration;
        String data;
        public OnDashDataEvent(long eventId, String data, long presentationTime, long duration)
        {
            this.eventId = eventId;
            this.data = data;
            this.presentationTime = presentationTime;
            this.duration = duration;
        }
    }
}
