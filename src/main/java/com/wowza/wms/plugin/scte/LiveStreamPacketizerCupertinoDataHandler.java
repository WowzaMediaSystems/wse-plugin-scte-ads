/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.scte;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wowza.util.BufferUtils;
import com.wowza.wms.amf.*;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.*;
import com.wowza.wms.media.mp3.model.idtags.*;
import com.wowza.wms.stream.IMediaStream;


public abstract class LiveStreamPacketizerCupertinoDataHandler extends LiveStreamPacketizerDataHandlerBase implements IHTTPStreamerCupertinoLivePacketizerDataHandler2
{
    protected final LiveStreamPacketizerCupertino liveStreamPacketizer;

    protected final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"));

    protected long streamStartTime = -1;
    protected long[] tcOffsets = {-1, -1, -1, -1};

    protected final Map<Long, OnDataEvent> events = new ConcurrentHashMap<>();

    public LiveStreamPacketizerCupertinoDataHandler(LiveStreamPacketizerCupertino liveStreamPacketizer, IMediaStream stream)
    {
        super(stream);

        this.liveStreamPacketizer = liveStreamPacketizer;
    }


    @Override
    public void onFillChunkStart(LiveStreamPacketizerCupertinoChunk chunk)
    {
        // common PDT code for all implementations
        int rendition = chunk.getRendition().getRendition();
        long chunkStartTime = chunk.getStartTimecode();
        if (tcOffsets[rendition - 1] == -1)
        {
            tcOffsets[rendition - 1] = chunkStartTime;
            streamStartTime = stream.getElapsedTime().getDate().getTime();
            logger.info(String.format("%s.onFillChunkStart [%s] rendition: %s, chunkStartTime: %d, streamStartTime: %d",
                    getClass().getSimpleName(), stream.getContextStr(), chunk.getRendition(), chunkStartTime, streamStartTime));
        }

        Instant pdt = Instant.ofEpochMilli(streamStartTime + (chunkStartTime - tcOffsets[rendition - 1]));
        Instant now = Instant.now();
        Duration diff = Duration.between(pdt, now);
        if (diff.abs().toMillis() > (long) liveStreamPacketizer.getChunkDurationTarget() * liveStreamPacketizer.getMaxChunkCount())
        {
            pdt = now;
            tcOffsets[rendition - 1] = -1;
        }
        String programDateTime = dateTimeFormatter.format(pdt);
        chunk.setProgramDateTime(programDateTime);

        ID3Frames id3Header = liveStreamPacketizer.getID3FramesHeader(chunk.getRendition());
        if (id3Header != null)
        {
            ID3V2FrameTextInformationUserDefined comment = new ID3V2FrameTextInformationUserDefined();

            comment.setDescription("programDateTime");
            comment.setValue(programDateTime);

            id3Header.clear();
            id3Header.putFrame(comment);
        }

        // Check if chunk needs to be adjusted
        events.entrySet().removeIf(e -> e.getValue().expired);
        events.forEach((id, event) -> {
            checkAdjustChunkEnd(event.startTime);
            checkAdjustChunkEnd(event.startTime + event.duration);
        });
    }

    @Override
    public void onFillChunkEnd(LiveStreamPacketizerCupertinoChunk chunk, long timecode)
    {
        // no-op
    }

    @Override
    public void onFillChunkMediaPacket(LiveStreamPacketizerCupertinoChunk chunk, CupertinoPacketHolder holder, AMFPacket packet)
    {

    }

    protected String getRawDataAsHexStr(AMFDataObj data)
    {
        String rawData = null;
        String encodedData = data.getString("rawData");
        if (encodedData != null)
        {
            byte[] dataBytes = Base64.getDecoder().decode(encodedData);
            rawData = "0x" + BufferUtils.encodeHexString(dataBytes).toUpperCase();
        }
        return rawData;
    }

    protected void checkAdjustChunkEnd(long timecode)
    {
        long chunkStart = liveStreamPacketizer.getSegmentStartKeyTimecode();
        long chunkEnd = liveStreamPacketizer.getSegmentStopKeyTimecode();
        if (timecode > chunkStart && timecode < chunkEnd)
        {
            logger.info(String.format("%s.checkAdjustChunkEnd [%s] timecode: %d, chunkStart: %d, chunkEnd: %d", getClass().getSimpleName(), stream.getContextStr(), timecode, chunkStart, chunkEnd));
            liveStreamPacketizer.setSegmentStopKeyTimecode(timecode);
        }
    }

    protected static class OnDataEvent
    {
        final long eventId;
        boolean expired = false;
        long startTime;
        long duration;
        String spliceOutData;
        String spliceInData = null;
        public OnDataEvent(long eventId)
        {
            this.eventId = eventId;
        }
    }
}
