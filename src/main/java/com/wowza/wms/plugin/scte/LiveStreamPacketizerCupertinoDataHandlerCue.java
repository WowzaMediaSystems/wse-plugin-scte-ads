/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.scte;

import com.wowza.wms.amf.*;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.*;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;
import com.wowza.wms.stream.IMediaStream;

public class LiveStreamPacketizerCupertinoDataHandlerCue extends LiveStreamPacketizerCupertinoDataHandler
{

    public LiveStreamPacketizerCupertinoDataHandlerCue(LiveStreamPacketizerCupertino liveStreamPacketizer, IMediaStream stream)
    {
        super(liveStreamPacketizer, stream);
    }

    @Override
    public void onFillChunkDataPacket(LiveStreamPacketizerCupertinoChunk chunk, CupertinoPacketHolder holder, AMFPacket packet, ID3Frames id3Frames)
    {
        int rendition = chunk.getRendition().getRendition();
        extractSCTEData(packet, rendition).ifPresent(data -> {
            AMFDataObj commandObj = data.getObject("command");
            String command = commandObj.getString("SpliceCommand");
            if (command.equalsIgnoreCase("insert"))
            {
                AMFDataObj eventObj = commandObj.getObject("event");
                long eventId = eventObj.getLong("eventID");
                events.computeIfAbsent(eventId, id -> {
                    boolean spliceOut = eventObj.getBoolean("outOfNetwork");
                    if (!spliceOut)
                        return null;
                    AMFDataObj spliceTimeObj = eventObj.getObject("spliceTime");
                    long spliceTimecode = spliceTimeObj.getLong("spliceTimeMS");
                    boolean durationFlag = eventObj.getBoolean("durationFlag");
                    long breakDuration = durationFlag ? (long) (eventObj.getDouble("breakDuration") / 90) : 0L;
                    if (breakDuration <= 0)
                        return null;
                    OnDataEvent newEvent = new OnDataEvent(id);
                    newEvent.startTime = spliceTimecode;
                    newEvent.duration = breakDuration;
                    newEvent.spliceOutData = data.getString("rawData");
                    checkAdjustChunkEnd(newEvent.startTime);
                    return newEvent;
                });
            }
        });
    }

    @Override
    public void onFillChunkEnd(LiveStreamPacketizerCupertinoChunk chunk, long timecode)
    {
        CupertinoUserManifestHeaders chunkHeaders = chunk.getUserManifestHeaders();
        events.forEach((id, event) -> {
            long elapsed = chunk.getStartTimecode() - event.startTime;
            // first chunk for event
            if (event.startTime >= chunk.getStartTimecode() && event.startTime < timecode)
            {
                String tag = String.format("EXT-OATCLS-SCTE35:%s", event.spliceOutData);
                chunkHeaders.addHeader(tag);
                tag = String.format("EXT-X-CUE-OUT:%.3f", event.duration / 1000d);
                chunkHeaders.addHeader(tag);
            }
            // continuation chunk
            else if (elapsed > 0 && elapsed < event.duration)
            {
                String tag = String.format("EXT-X-CUE-OUT-CONT:ElapsedTime=%.3f,Duration=%.3f, SCTE35=%s",
                        elapsed / 1000d, event.duration / 1000d, event.spliceOutData);
                chunkHeaders.addHeader(tag);
            }
            // first chunk after event
            else if (elapsed >= event.duration)
            {
                chunkHeaders.addHeader("EXT-X-CUE-IN");
                event.expired = true;
            }
        });
    }
}
