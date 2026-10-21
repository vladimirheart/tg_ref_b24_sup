package com.example.panel.storage;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.util.*;

/** Read proof uses frozen canonical ticket fields, never today's bindings or catalog. */
public final class QuestionnaireMediaStorageProof {
    public static final int MAX_BYTES=10*1024*1024;
    private QuestionnaireMediaStorageProof() { }
    public static boolean isScoped(String key) {
        if(key==null) return false;
        return key.strip().replace('\\','/').replaceFirst("^/+","").toLowerCase(Locale.ROOT).startsWith("questionnaires/telegram/");
    }
    public static boolean matches(ResultSet row,String key) throws SQLException {
        Long channel=(Long)row.getObject("proof_channel"),user=(Long)row.getObject("proof_user"),business=(Long)row.getObject("proof_business"),provider=(Long)row.getObject("proof_provider");
        String catalog=row.getString("proof_catalog"),sha=row.getString("content_hash");Long size=(Long)row.getObject("size");
        if(!"USER_SELECTION".equals(row.getString("proof_source")) || !"telegram".equals(row.getString("proof_platform"))
                || channel==null || channel<=0 || user==null || user<=0 || provider==null || provider<=0 || (business!=null && business<=0)
                || !Objects.equals(channel,row.getObject("proof_history_channel")) || !Objects.equals(channel,row.getObject("proof_metadata_channel"))
                || catalog==null || !catalog.matches("[a-f0-9]{64}") || sha==null || !sha.matches("[a-f0-9]{64}")
                || size==null || size<1 || size>MAX_BYTES) return false;
        String prefix="questionnaires/telegram/"+channel+"/"+(business==null?"general":"business-"+business)+"/"+user+"/"+catalog+"/"+provider+"/";
        return key!=null && key.startsWith(prefix) && key.substring(prefix.length()).matches(sha+"\\.[a-z0-9]{1,10}");
    }
    public static byte[] read(AttachmentObjectStorageService.StoredBinary binary,String hash,Long size) throws IOException {
        if(hash==null || !hash.matches("[a-f0-9]{64}") || size==null || size<1 || size>MAX_BYTES || binary.size()!=size) throw new IOException("Invalid media size/hash");
        byte[] bytes=binary.inputStream().readNBytes(MAX_BYTES+1);
        try {
            if(bytes.length!=size || !hash.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) throw new IOException("Media bytes changed");
        } catch(java.security.NoSuchAlgorithmException ex) {throw new IllegalStateException(ex);}
        return bytes;
    }
}
