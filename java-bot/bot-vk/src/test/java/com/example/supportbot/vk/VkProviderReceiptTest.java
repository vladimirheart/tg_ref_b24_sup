package com.example.supportbot.vk;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.service.*;
import com.example.supportbot.config.VkBotProperties;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class VkProviderReceiptTest {
    @ParameterizedTest @ValueSource(strings={"ok","null-id","zero-id","negative-id","exception"})
    void preservesPositiveSdkIdWithoutRetryingOrNarrowingAnotherRecipient(String change)throws Exception{
        var bot=mock(VkSupportBot.class,CALLS_REAL_METHODS);var config=new VkBotProperties();config.setEnabled(true);config.setGroupId(1);config.setToken("test-only");
        var client=mock(VkApiClient.class,RETURNS_DEEP_STUBS);
        var query=client.messages().sendDeprecated(new GroupActor(1L,"test-only"));
        when(client.messages().sendDeprecated(any(GroupActor.class))).thenReturn(query);
        when(query.peerId(anyLong())).thenReturn(query);when(query.randomId(anyInt())).thenReturn(query);when(query.message(anyString())).thenReturn(query);
        if(change.equals("exception"))when(query.execute()).thenThrow(new ClientException("test failure"));
        else when(query.execute()).thenReturn(change.equals("null-id")?null:change.equals("zero-id")?0:change.equals("negative-id")?-1:42);
        ReflectionTestUtils.setField(bot,"properties",config);ReflectionTestUtils.setField(bot,"vkClient",client);
        var provider=new VkOutboundMessenger(bot,new BotRuntimeSenderIdentity(1L,"vk"));
        var result=provider.sendToUserWithReceipt(77L,"Ю test");
        assertThat(result.accepted()).isEqualTo(change.equals("ok"));assertThat(result.messageId()).isEqualTo(change.equals("ok")?"42":null);
        verify(query,times(1)).execute();verify(query).peerId(77L);verify(query).message("Ю test");
        assertThat(provider.sendToUserWithReceipt(9007199254740993L,"test").accepted()).isFalse();
        verify(query,times(1)).execute();verify(bot,never()).sendDirectMessage(anyInt(),anyString());
    }
}
