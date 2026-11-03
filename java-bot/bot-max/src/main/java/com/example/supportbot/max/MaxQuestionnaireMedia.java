package com.example.supportbot.max;

import com.example.supportbot.service.QuestionnaireMedia;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;

/** Describe the complete direct batch before I/O. Forwarded/opaque/ambiguous shapes remain explicitly unsupported. */
final class MaxQuestionnaireMedia {
    static final Set<String> TYPES=Set.of("document","photo","audio","video");
    record Item(int slot,String type,String url,String name) {}
    private MaxQuestionnaireMedia() {}
    static List<Item> describe(JsonNode message,List<MaxInboundPayloadSupport.IncomingAttachment> decoded) {
        if(message.hasNonNull("link"))throw invalid();
        JsonNode body=message.path("body"),raw=body.path("attachments");
        if(!raw.isArray() || raw.isEmpty() || raw.size()>QuestionnaireMedia.MAX_FILES || decoded.size()!=raw.size()
                || (message.hasNonNull("attachments") && !message.path("attachments").isEmpty())
                || (body.hasNonNull("media") && !body.path("media").isEmpty()) || (body.hasNonNull("files") && !body.path("files").isEmpty()))throw invalid();
        var result=new ArrayList<Item>();
        for(int i=0;i<raw.size();i++) {
            String type=switch(Objects.toString(decoded.get(i).type(),"").toLowerCase(Locale.ROOT)) {
                case "file","document"->"document";case "image","photo"->"photo";case "voice","audio"->"audio";case "video"->"video";default->throw invalid();
            };
            URI uri=MaxApiClient.trustedAttachmentUri(decoded.get(i).url());
            if(uri==null || uri.getFragment()!=null || (uri.getPort()!=-1&&uri.getPort()!=443))throw invalid();
            for(var size:List.of(raw.get(i).path("size"),raw.get(i).path("payload").path("size")))if(!size.isMissingNode()&&!size.isNull()
                    && (!size.isIntegralNumber()||!size.canConvertToLong()||size.longValue()<1||size.longValue()>QuestionnaireMedia.MAX_BYTES))throw invalid();
            result.add(new Item(i+1,type,uri.toString(),decoded.get(i).name()));
        }
        return List.copyOf(result);
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Unsupported MAX questionnaire attachment");}
}
