package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.MaxBotProperties;
import com.example.supportbot.service.QuestionnaireMedia;
import java.io.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

@SuppressWarnings({"unchecked","rawtypes"})
class MaxQuestionnaireDownloadTest {
    final HttpClient http=mock(HttpClient.class);
    final MaxApiClient client=new MaxApiClient(new MaxBotProperties(),http);
    final Flow.Subscription subscription=mock(Flow.Subscription.class);
    final String url="https://cdn.max.ru/private?secret=not-logged";
    @BeforeEach void redirectsOff() {when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);}
    void response(int status,List<String> lengths,byte[] bytes) {
        var info=mock(HttpResponse.ResponseInfo.class);when(info.statusCode()).thenReturn(status);
        var headers=new LinkedHashMap<String,List<String>>();if(!lengths.isEmpty())headers.put("Content-Length",lengths);headers.put("Content-Type",List.of("application/pdf"));
        when(info.headers()).thenReturn(HttpHeaders.of(headers,(a,b)->true));
        when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
            HttpResponse.BodyHandler<MaxApiClient.DownloadedQuestionnaireAttachment> handler=call.getArgument(1);
            var body=handler.apply(info);body.onSubscribe(subscription);body.onNext(List.of(ByteBuffer.wrap(bytes)));body.onComplete();
            return body.getBody().toCompletableFuture().thenApply(data->{HttpResponse<MaxApiClient.DownloadedQuestionnaireAttachment> result=mock(HttpResponse.class);when(result.body()).thenReturn(data);return result;});
        });
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void optionalLengthStillVerifiesBytesAndDoesNotSendCredentials(boolean length) throws Exception {
        response(200,length?List.of("3"):List.of(),new byte[]{1,2,3});
        var data=client.downloadQuestionnaireAttachment(url);assertThat(data.bytes()).containsExactly((byte)1,(byte)2,(byte)3);
        assertThat(data.contentType()).isEqualTo("application/pdf");verify(subscription,never()).cancel();
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(http).sendAsync(request.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().method()).isEqualTo("GET");assertThat(request.getValue().headers().map()).isEmpty();
        assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(60));
    }
    @ParameterizedTest @ValueSource(strings={"http://cdn.max.ru/file","https://example.org/private?secret=not-logged","https://max.ru.evil.org/file","https://max.ru@evil.org/file","https://user@max.ru/file","https://cdn.max.ru:444/file","https://cdn.max.ru:0/file","https://cdn.max.ru/file#fragment","https://cdn.max.ru/%oops?secret=not-logged","file:///private","https://127.0.0.1/file","https://[::1]/file"})
    void invalidUrlNeverReachesTransport(String value) {
        clearInvocations(http);
        assertThatThrownBy(()->client.downloadQuestionnaireAttachment(value)).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
        verifyNoInteractions(http);
    }
    @ParameterizedTest @ValueSource(strings={"max.ru","cdn.max.ru","iu.oneme.ru","omub.okcdn.ru","cdn.mycdn.me"})
    void existingTrustedCdnPerimeterAndDefaultTlsPortArePreserved(String host) throws Exception {
        response(200,List.of("1"),new byte[]{1});assertThat(client.downloadQuestionnaireAttachment("https://"+host+":443/file").bytes()).containsExactly((byte)1);
    }
    @ParameterizedTest @EnumSource(value=HttpClient.Redirect.class,names={"NORMAL","ALWAYS"})
    void redirectingClientCannotCrossThePerimeter(HttpClient.Redirect mode) {
        when(http.followRedirects()).thenReturn(mode);
        assertThatThrownBy(()->client.downloadQuestionnaireAttachment(url)).isInstanceOf(IllegalStateException.class);
        verify(http,never()).sendAsync(any(),any(HttpResponse.BodyHandler.class));
    }
    @ParameterizedTest @ValueSource(ints={201,204,206,301,302,307,308,400,403,404,500})
    void nonCompleteSuccessOrRedirectCancelsBody(int status) {
        response(status,List.of("3"),new byte[]{1,2,3});
        assertThatThrownBy(()->client.downloadQuestionnaireAttachment(url)).isInstanceOf(IOException.class).hasMessageNotContaining("secret").hasCause(null);
        verify(subscription,atLeastOnce()).cancel();
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","10485761","9223372036854775808","+3","3,3","bad"})
    void invalidLengthCancelsWithoutSuccess(String length) {
        response(200,List.of(length),new byte[]{1,2,3});
        assertThatThrownBy(()->client.downloadQuestionnaireAttachment(url)).isInstanceOf(IOException.class);verify(subscription,atLeastOnce()).cancel();
    }
    @Test void duplicateLengthIsRejected() {
        response(200,List.of("3","3"),new byte[]{1,2,3});assertThatThrownBy(()->client.downloadQuestionnaireAttachment(url)).isInstanceOf(IOException.class);verify(subscription).cancel();
    }
    @ParameterizedTest @ValueSource(ints={0,2,4,10485761})
    void emptyShortLongAndOversizeBodiesAreRejectedAndCancelled(int size) {
        response(200,size==0||size>QuestionnaireMedia.MAX_BYTES?List.of():List.of("3"),new byte[size]);
        assertThatThrownBy(()->client.downloadQuestionnaireAttachment(url)).isInstanceOf(IOException.class);verify(subscription).cancel();
    }
    @Test void exactLimitIsAccepted() throws Exception {
        byte[] bytes=new byte[QuestionnaireMedia.MAX_BYTES];bytes[bytes.length-1]=19;
        response(200,List.of(String.valueOf(bytes.length)),bytes);assertThat(client.downloadQuestionnaireAttachment(url).bytes()).isEqualTo(bytes);verify(subscription,never()).cancel();
    }
    @Test void synchronousTransportFailureAlsoCancelsAnAlreadySubscribedBody() {
        var info=mock(HttpResponse.ResponseInfo.class);when(info.statusCode()).thenReturn(200);when(info.headers()).thenReturn(HttpHeaders.of(Map.of(),(a,b)->true));
        when(http.sendAsync(any(),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
            HttpResponse.BodyHandler<?> handler=call.getArgument(1);var body=handler.apply(info);body.onSubscribe(subscription);throw new IllegalStateException(url);
        });
        assertThatThrownBy(()->client.downloadQuestionnaireAttachment(url)).isInstanceOf(IOException.class).hasCause(null).hasMessageNotContaining("secret");
        verify(subscription).cancel();
    }
    @ParameterizedTest @ValueSource(strings={"timeout","interrupted","transport"})
    void incompleteOrFailedTransportCancelsEvenALateSubscriber(String kind) throws Exception {
        var pending=new CompletableFuture<HttpResponse<MaxApiClient.DownloadedQuestionnaireAttachment>>();
        when(http.sendAsync(any(),any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        if(kind.equals("transport"))pending.completeExceptionally(new IOException(url));
        try {
            if(kind.equals("interrupted"))Thread.currentThread().interrupt();
            assertThatThrownBy(()->client.downloadQuestionnaireAttachment(url,Duration.ofMillis(20)))
                .isInstanceOf(kind.equals("interrupted")?InterruptedException.class:IOException.class).hasMessageNotContaining("secret");
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(kind.equals("interrupted"));
            if(!kind.equals("transport"))assertThat(pending.isCancelled()).isTrue();
            var handler=org.mockito.ArgumentCaptor.forClass(HttpResponse.BodyHandler.class);verify(http).sendAsync(any(),handler.capture());
            var info=mock(HttpResponse.ResponseInfo.class);when(info.statusCode()).thenReturn(200);when(info.headers()).thenReturn(HttpHeaders.of(Map.of(),(a,b)->true));
            var late=handler.getValue().apply(info);late.onSubscribe(subscription);verify(subscription).cancel();
        } finally {Thread.interrupted();}
    }
}
