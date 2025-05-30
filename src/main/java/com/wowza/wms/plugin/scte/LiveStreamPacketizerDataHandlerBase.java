/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.scte;

import com.wowza.wms.amf.*;
import com.wowza.wms.logging.*;
import com.wowza.wms.stream.IMediaStream;

import java.util.Optional;

import static com.wowza.wms.amf.AMFData.*;
import static com.wowza.wms.rtp.depacketizer.RTPDePacketizerMPEGTSMonitorCUE.AMF_SCTE_HANDLER_NAME;

public class LiveStreamPacketizerDataHandlerBase
{
    protected final WMSLogger logger;
    protected final IMediaStream stream;

    public LiveStreamPacketizerDataHandlerBase(IMediaStream stream)
    {
        logger = WMSLoggerFactory.getLoggerObj(getClass(), stream.getStreams().getAppInstance());
        this.stream = stream;
    }

    protected Optional<AMFDataObj> extractSCTEData(AMFPacket packet, int rendition)
    {
        byte[] buffer = packet.getData();
        if (buffer == null || packet.getSize() <= 2)
            return Optional.empty();
        int offset = buffer[0] != 0 ? 0 : 1;
        AMFDataList amfList = new AMFDataList(buffer, offset, buffer.length - offset);
        System.out.println(stream.getName() + ": " + rendition + ": " + amfList);
        if (amfList.size() <= 1)
            return Optional.empty();
        if (amfList.get(0).getType() != DATA_TYPE_STRING && amfList.get(1).getType() != DATA_TYPE_OBJECT)
            return Optional.empty();
        String handlerName = amfList.getString(0);
        AMFDataObj data = amfList.getObject(1);
        if (!handlerName.equalsIgnoreCase(AMF_SCTE_HANDLER_NAME))
            return Optional.empty();
        logger.info(String.format("%s.extractSCTEData [%s] timecode: %d, data: %s", getClass().getSimpleName(), stream.getContextStr(), packet.getAbsTimecode(), data));
        return Optional.ofNullable(data);
    }
}
