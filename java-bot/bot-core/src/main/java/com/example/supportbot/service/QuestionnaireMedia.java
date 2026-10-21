package com.example.supportbot.service;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/** Storage identity is independent of display names and the operator chat. */
public final class QuestionnaireMedia {
    public static final int MAX_BYTES=10*1024*1024, MAX_FILES=10;
    public static final Set<String> TYPES=Set.of("document","photo","video","voice","audio","animation","sticker","video_note");
    private QuestionnaireMedia() { }
    public static String prefix(long channelId,Long businessId,long userId,String catalogSha256,long messageId) {
        if(channelId<=0 || userId<=0 || messageId<=0 || (businessId!=null && businessId<=0)
                || catalogSha256==null || !catalogSha256.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid questionnaire media owner");
        return "questionnaires/telegram/"+channelId+"/"+(businessId==null?"general":"business-"+businessId)+"/"
            +userId+"/"+catalogSha256+"/"+messageId+"/";
    }
    public static boolean matches(String key,long channelId,Long businessId,long userId,String catalogSha256,long messageId) {
        String prefix=prefix(channelId,businessId,userId,catalogSha256,messageId);
        return key!=null && key.startsWith(prefix) && key.substring(prefix.length()).matches("[a-f0-9]{64}\\.[a-z0-9]{1,10}");
    }
    /** VK's conversation message ID is scoped to the private peer (the user); each item has a stable 1-based slot. */
    public static String vkPrefix(long channelId,Long businessId,long userId,String catalogSha256,long messageId,int slot) {
        String telegram=prefix(channelId,businessId,userId,catalogSha256,messageId);
        if(slot<1 || slot>MAX_FILES) throw new IllegalArgumentException("Invalid VK media slot");
        return telegram.replace("questionnaires/telegram/","questionnaires/vk/")+slot+"/";
    }
    public static boolean matchesVk(String key,long channelId,Long businessId,long userId,String catalogSha256,long messageId) {
        String prefix=prefix(channelId,businessId,userId,catalogSha256,messageId).replace("questionnaires/telegram/","questionnaires/vk/");
        return key!=null && key.startsWith(prefix) && key.substring(prefix.length()).matches("(?:[1-9]|10)/[a-f0-9]{64}\\.[a-z0-9]{1,10}");
    }
    public static String sha256(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException ex) {throw new IllegalStateException(ex);}
    }
}
