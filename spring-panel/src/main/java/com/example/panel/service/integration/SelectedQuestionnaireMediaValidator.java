package com.example.panel.service.integration;

import com.example.panel.storage.AttachmentObjectStorageService;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;

/** Verifies owned bytes before any new ticket or child INSERT. Existing-root replay bypasses storage. */
@Service
public class SelectedQuestionnaireMediaValidator {
    static final int MAX_BYTES=10*1024*1024, MAX_FILES=10;
    private static final Set<String> TYPES=Set.of("document","photo","video","voice","audio","animation","sticker","video_note");
    private final AttachmentObjectStorageService storage;
    public SelectedQuestionnaireMediaValidator(AttachmentObjectStorageService storage) {this.storage=storage;}
    public record Verified(long size,String sha256) { }

    public Map<String,Verified> validate(ConversationTicketCreatedEvent event,int version) {
        var result=new LinkedHashMap<String,Verified>();
        if(event.historyEntries()==null) return result;
        for(var entry:event.historyEntries()) {
            if(entry==null || entry.attachmentPath()==null) continue;
            boolean vk="vk".equals(event.platform());
            if(!"telegram".equals(event.platform()) && !vk) {
                if(entry.attachmentPath().startsWith("questionnaires/")) throw invalid();
                continue; // Earlier MAX attachment protocols retain their own admission rules.
            }
            if(version!=4 || event.userId()==null || event.userId()<=0 || !Objects.equals(event.userId(),entry.userId())
                    || !(vk?Set.of("document","photo","audio_message"):TYPES).contains(Objects.toString(entry.messageType(),"")) || entry.occurredAt()==null
                    || entry.providerMessageId()==null || !entry.providerMessageId().matches("[1-9][0-9]{0,18}")
                    || (entry.attachmentName()!=null && (entry.attachmentName().length()>255
                        || entry.attachmentName().chars().anyMatch(c->c<32 || c==127 || c=='/' || c=='\\')))) throw invalid();
            try {if(Long.parseLong(entry.providerMessageId())<=0) throw invalid();} catch(NumberFormatException ex) {throw invalid();}
            String prefix="questionnaires/"+(vk?"vk":"telegram")+"/"+event.channelId()+"/"+(event.businessId()==null?"general":"business-"+event.businessId())
                +"/"+event.userId()+"/"+event.catalogSha256()+"/"+entry.providerMessageId()+"/";
            String key=entry.attachmentPath();
            if(!key.startsWith(prefix) || !key.substring(prefix.length()).matches((vk?"(?:[1-9]|10)/":"")+"[a-f0-9]{64}\\.[a-z0-9]{1,10}")
                    || result.size()>=MAX_FILES || result.containsKey(key)) throw invalid();
            // Telegram has one rendition; VK has one item per stable slot within the same conversation message.
            String itemPrefix=vk?key.substring(0,key.lastIndexOf('/')+1):prefix;
            if(result.keySet().stream().anyMatch(previous->previous.startsWith(itemPrefix))) throw invalid();
            try(var file=storage.openDialogAttachmentByStorageKey(key)) {
                if(file.size()<=0 || file.size()>MAX_BYTES) throw invalid();
                byte[] bytes=file.inputStream().readNBytes(MAX_BYTES+1);
                String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if(bytes.length!=file.size() || bytes.length>MAX_BYTES || !key.substring(key.lastIndexOf('/')+1).startsWith(sha+".")) throw invalid();
                result.put(key,new Verified(bytes.length,sha));
            } catch(Exception ex) {throw new IllegalStateException("Selected questionnaire media is unavailable or changed",ex);}
        }
        return result;
    }
    private static IllegalStateException invalid() {return new IllegalStateException("Invalid selected questionnaire media proof");}
}
