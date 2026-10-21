package com.example.supportbot.vk;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.Gson;
import com.vk.api.sdk.objects.messages.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VkQuestionnaireMediaTest {
    private Message document() {
        return new Gson().fromJson("""
            {"from_id":1001,"peer_id":1001,"conversation_message_id":33,"date":1792584000,"attachments":[
              {"type":"doc","doc":{"url":"https://cdn.userapi.com/file?token=opaque","title":"C:\\\\upload\\\\proof.HTML","ext":"HTML","size":10}}]}
            """,Message.class);
    }
    @ParameterizedTest @ValueSource(strings={"http://cdn.userapi.com/file","https://localhost/file","https://127.0.0.1/file","https://userapi.com.invalid/file","https://userapi.com@evil.invalid/file","https://user:pass@cdn.userapi.com/file","https://cdn.userapi.com:8443/file","https://cdn.userapi.com/file#fragment","file:///tmp/file"})
    void rejectsNonProviderOrUnsafeDownloadUrls(String url) {
        assertThatThrownBy(()->VkQuestionnaireMedia.url(url)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void descriptorsContainOnlySanitizedNameAndStableSlotWithLowercaseExtension() {
        var item=VkQuestionnaireMedia.describe(document()).get(0);
        assertThat(item.slot()).isEqualTo(1);assertThat(item.type()).isEqualTo("document");assertThat(item.extension()).isEqualTo("html");assertThat(item.name()).isEqualTo("proof.HTML");
        assertThat(item.url().getRawQuery()).isEqualTo("token=opaque");
    }
    @ParameterizedTest @ValueSource(strings={"missing-time","missing-provider","oversize","unsafe-extension","long-name"})
    void validatesProviderIdentitySizeAndUntrustedFileHintsBeforeDownload(String mutation) {
        var message=document();var doc=message.getAttachments().get(0).getDoc();
        switch(mutation) {
            case "missing-time"->message.setDate(null);case "missing-provider"->message.setConversationMessageId(null);
            case "oversize"->doc.setSize(10485761);case "unsafe-extension"->doc.setExt("../bin");case "long-name"->doc.setTitle("a".repeat(300));
        }
        if(mutation.equals("unsafe-extension"))assertThat(VkQuestionnaireMedia.describe(message).get(0).extension()).isEqualTo("bin");
        else if(mutation.equals("long-name"))assertThat(VkQuestionnaireMedia.describe(message).get(0).name()).hasSize(255);
        else assertThatThrownBy(()->VkQuestionnaireMedia.describe(message)).isInstanceOfAny(IllegalArgumentException.class,IllegalStateException.class);
    }
}
