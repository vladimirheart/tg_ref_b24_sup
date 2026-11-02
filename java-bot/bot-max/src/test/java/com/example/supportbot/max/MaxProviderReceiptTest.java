package com.example.supportbot.max;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.MaxBotProperties;
import com.example.supportbot.service.*;
import java.net.http.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MaxProviderReceiptTest {
    @ParameterizedTest @ValueSource(strings={"ok","wrong-user","string-user","fraction-user","missing-id","bad-id","bad-json","http-error","io-error","interrupted"})
    @SuppressWarnings({"unchecked","rawtypes"})
    void usesOnlyBoundStructuredMaxAcceptance(String change)throws Exception{
        long user=9007199254740993L;var config=new MaxBotProperties();config.setToken("test-only");
        var http=mock(HttpClient.class);HttpResponse<String> response=mock(HttpResponse.class);
        String recipient=change.equals("wrong-user")?"9007199254740994":change.equals("string-user")?"\"9007199254740993\"":change.equals("fraction-user")?"9007199254740993.0":"9007199254740993";
        String mid=change.equals("missing-id")?"null":change.equals("bad-id")?"\"https://bad\"":"\"mid.42\"";
        when(response.body()).thenReturn(change.equals("bad-json")?"{":"{\"message\":{\"recipient\":{\"user_id\":"+recipient+"},\"body\":{\"mid\":"+mid+"}}}");
        when(response.statusCode()).thenReturn(change.equals("http-error")?503:200);
        if(change.equals("io-error"))doThrow(new java.io.IOException("test-only")).when(http).send(any(),any(HttpResponse.BodyHandler.class));
        else if(change.equals("interrupted"))doThrow(new InterruptedException("test-only")).when(http).send(any(),any(HttpResponse.BodyHandler.class));
        else doReturn(response).when(http).send(any(),any(HttpResponse.BodyHandler.class));
        var client=new MaxApiClient(config,http);var provider=new MaxOutboundMessenger(client,new BotRuntimeSenderIdentity(1L,"max"));
        try{
            var result=provider.sendToUserWithReceipt(user,"Ю test");
            assertThat(result.accepted()).isEqualTo(change.equals("ok"));assertThat(result.messageId()).isEqualTo(change.equals("ok")?"mid.42":null);
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(change.equals("interrupted"));
            var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(1)).send(request.capture(),any(HttpResponse.BodyHandler.class));
            assertThat(request.getValue().uri().getRawQuery()).isEqualTo("user_id=9007199254740993");
            assertThat(request.getValue().method()).isEqualTo("POST");
        }finally{Thread.interrupted();}
    }
}
