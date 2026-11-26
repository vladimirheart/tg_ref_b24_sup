package com.example.panel.service;

import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.*;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Private internal-message binaries; no customer transport or public attachment registration. */
@Service
public class DialogInternalAttachmentService {
    public static final long MAX_BYTES=50L*1024*1024;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(DialogInternalAttachmentService.class);
    private final AttachmentObjectStorageService storage;
    private final DialogOperatorCollaborationService messages;
    public DialogInternalAttachmentService(AttachmentObjectStorageService storage,DialogOperatorCollaborationService messages){
        this.storage=storage;this.messages=messages;
    }
    public boolean send(DialogOperatorCollaborationService.Context expected,Authentication auth,UUID requestId,String body,List<MultipartFile> files) throws IOException {
        if(expected==null||requestId==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var current=messages.find(expected.scope().ticketPublicId(),auth).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!current.scope().equals(expected.scope())||!current.canWrite())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(body==null)body="";
        if(body.isBlank())body="";
        if(body.length()>10000||body.indexOf('\0')>=0||files==null||files.isEmpty()||files.size()>10)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"До 10 файлов, суммарно до 50 МБ");
        long total=0;
        for(var file:files){
            if(file==null||file.isEmpty()||file.getSize()<=0||file.getSize()>MAX_BYTES)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Выберите непустые файлы, суммарно до 50 МБ");
            safeName(file.getOriginalFilename());
            total+=file.getSize();if(total>MAX_BYTES)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Суммарно до 50 МБ");
        }
        var prepared=new ArrayList<DialogOperatorCollaborationService.PreparedAttachment>();
        var keys=new ArrayList<String>();
        var attempt=new DialogOperatorCollaborationService.SaveAttempt();boolean saving=false;
        try {
            for(var file:files){
                UUID id=UUID.randomUUID();String key=expected.scope().ticketPublicId()+"/"+id;keys.add(key);
                MessageDigest digest;
                try {digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
                String mime;
                try(var raw=file.getInputStream();var input=new DigestInputStream(raw,digest)){
                    byte[] head=input.readNBytes(16);mime=TaskAttachmentService.imageType(head);
                    try(var binary=storage.storeInternalDialogAttachment(key,mime,new SequenceInputStream(new ByteArrayInputStream(head),input))){
                        if(binary.size()!=file.getSize())throw new IOException("Stored attachment size mismatch");
                    }
                }
                prepared.add(new DialogOperatorCollaborationService.PreparedAttachment(id,key,safeName(file.getOriginalFilename()),mime,
                    file.getSize(),HexFormat.of().formatHex(digest.digest())));
            }
            saving=true;
            var result=messages.save(expected,auth,requestId,body,prepared,attempt);
            if(!result.created())cleanup(keys);
            return result.success();
        } catch(IOException|RuntimeException failure){
            // An uncertain DB commit may already reference these objects. Keep them private for reconciliation.
            if(!saving||attempt.rolledBack())cleanup(keys);
            else log.warn("Internal attachment commit result unknown; retained {} private objects",keys.size());
            throw failure;
        }
    }
    public Opened open(String publicId,UUID id,Authentication auth) throws IOException {
        var metadata=messages.file(publicId,auth,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!Objects.equals(metadata.key(),publicId+"/"+id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        com.example.panel.storage.AttachmentObjectStorageService.StoredBinary binary;
        try {binary=storage.openInternalDialogAttachment(metadata.key());}
        catch(FileNotFoundException|java.nio.file.NoSuchFileException missing){throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
        catch(software.amazon.awssdk.services.s3.model.S3Exception failure){
            throw new ResponseStatusException(failure.statusCode()==404?HttpStatus.NOT_FOUND:HttpStatus.SERVICE_UNAVAILABLE);
        }
        if(binary.size()!=metadata.size()){binary.close();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);}
        return new Opened(metadata,binary);
    }
    private void cleanup(List<String> keys){
        for(String key:keys)try{storage.deleteInternalDialogAttachment(key);}
        catch(IOException|RuntimeException failure){log.warn("Internal attachment cleanup deferred");}
    }
    static String safeName(String name){
        if(name==null||name.isBlank()||name.length()>240||name.equals(".")||name.equals("..")
            ||name.chars().anyMatch(c->c<32||c==127||c=='/'||c=='\\'))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Недопустимое имя файла");
        return name;
    }
    public record Opened(DialogOperatorCollaborationService.FileRecord metadata,AttachmentObjectStorageService.StoredBinary binary){}
}

