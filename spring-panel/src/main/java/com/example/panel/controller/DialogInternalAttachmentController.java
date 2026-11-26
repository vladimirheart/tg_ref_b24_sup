package com.example.panel.controller;

import com.example.panel.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/operator-dialogs/{publicId}/internal-messages")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class DialogInternalAttachmentController {
    private final DialogOperatorCollaborationService messages;
    private final DialogInternalAttachmentService files;
    public DialogInternalAttachmentController(DialogOperatorCollaborationService messages,DialogInternalAttachmentService files){
        this.messages=messages;this.files=files;
    }
    @PostMapping(value="/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@PathVariable String publicId,@RequestPart("command") DialogOperatorCollaborationController.Command command,
            @RequestPart("files") List<MultipartFile> attachments,Authentication auth) throws IOException {
        var state=messages.find(publicId,auth).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!state.canWrite())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(!DialogOperatorCollaborationController.matches(publicId,command,state.scope()))throw new ResponseStatusException(HttpStatus.CONFLICT);
        UUID request;
        try{request=UUID.fromString(command.requestId());}catch(Exception invalid){throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}
        if(!files.send(state,auth,request,command.body(),attachments))throw new ResponseStatusException(HttpStatus.CONFLICT,"Данные или права изменились");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("success",true));
    }
    @GetMapping("/attachments/{fileId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String publicId,@PathVariable String fileId,
            @RequestParam(defaultValue="false") boolean preview,@RequestHeader(value=HttpHeaders.RANGE,required=false) String range,
            Authentication auth) throws IOException {
        UUID id;
        try{id=UUID.fromString(fileId);if(!id.toString().equals(fileId))throw new IllegalArgumentException();}
        catch(IllegalArgumentException invalid){throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
        var opened=files.open(publicId,id,auth);var binary=opened.binary();var metadata=opened.metadata();
        boolean inline=preview&&Set.of("image/png","image/jpeg","image/gif","image/webp","video/mp4","video/webm","audio/wav","audio/mpeg","audio/ogg").contains(metadata.mimeType());
        var disposition=(inline?ContentDisposition.inline():ContentDisposition.attachment()).filename(metadata.name(),StandardCharsets.UTF_8).build();
        long start=0,end=binary.size()-1;boolean partial=inline&&range!=null;
        if(partial)try{
            var ranges=HttpRange.parseRanges(range);
            if(ranges.size()!=1)throw new IllegalArgumentException();
            start=ranges.get(0).getRangeStart(binary.size());end=ranges.get(0).getRangeEnd(binary.size());
            if(start>end||start>=binary.size())throw new IllegalArgumentException();
            binary.inputStream().skipNBytes(start);
        }catch(IllegalArgumentException invalid){binary.close();return ResponseEntity.status(416).header(HttpHeaders.CONTENT_RANGE,"bytes */"+binary.size()).build();}
        catch(IOException failure){binary.close();throw failure;}
        long length=end-start+1;
        var stream=new FilterInputStream(binary.inputStream()){
            long remaining=length;
            @Override public int read() throws IOException{if(remaining==0)return -1;int v=super.read();if(v>=0)remaining--;return v;}
            @Override public int read(byte[] bytes,int offset,int count) throws IOException{
                if(count==0)return 0;if(remaining==0)return -1;
                int read=in.read(bytes,offset,(int)Math.min(count,remaining));if(read>0)remaining-=read;return read;
            }
        };
        var response=ResponseEntity.status(partial?206:200).cacheControl(CacheControl.noStore());
        if(inline)response.header(HttpHeaders.ACCEPT_RANGES,"bytes");
        if(partial)response.header(HttpHeaders.CONTENT_RANGE,"bytes "+start+"-"+end+"/"+binary.size());
        return response.header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","sandbox; default-src 'none'").header("Cross-Origin-Resource-Policy","same-origin")
            .contentType(inline?MediaType.parseMediaType(metadata.mimeType()):MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(length).body(new InputStreamResource(stream));
    }
}

