package com.example.supportbot.max;

import com.example.supportbot.service.QuestionnaireMedia;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded subscriber: no unbounded InputStream escapes; deadline includes the complete body. */
final class MaxQuestionnaireDownload {
    private MaxQuestionnaireDownload() {}
    static MaxApiClient.DownloadedQuestionnaireAttachment download(HttpClient client,URI uri,Duration deadline)
            throws IOException,InterruptedException {
        var subscriber=new AtomicReference<LimitedBody>();
        var cancelled=new AtomicBoolean();
        var request=HttpRequest.newBuilder(uri).timeout(deadline).GET().build();
        CompletableFuture<HttpResponse<MaxApiClient.DownloadedQuestionnaireAttachment>> response;
        try {
            response=client.sendAsync(request,info->{var body=new LimitedBody(info);subscriber.set(body);if(cancelled.get())body.cancel();return body;});
        } catch(RuntimeException failure) {cancelled.set(true);cancel(subscriber);throw new IOException("MAX scoped media transport failed");}
        try {
            var completed=response.get(deadline.toMillis(),TimeUnit.MILLISECONDS);
            if(completed==null || completed.body()==null)throw new IOException("MAX scoped media body missing");
            return completed.body();
        } catch(InterruptedException interrupted) {
            cancelled.set(true);response.cancel(true);cancel(subscriber);Thread.currentThread().interrupt();throw interrupted;
        } catch(ExecutionException failure) {
            cancelled.set(true);response.cancel(true);cancel(subscriber);
            if(failure.getCause() instanceof com.example.supportbot.service.AttachmentService.MediaSizeException size)throw size;
            throw new IOException("MAX scoped media download failed or exceeded its deadline");
        } catch(TimeoutException|CancellationException failure) {
            cancelled.set(true);response.cancel(true);cancel(subscriber);
            // CDN URLs can contain credentials: neither the URL nor transport exception becomes persisted/logged metadata.
            throw new IOException("MAX scoped media download failed or exceeded its deadline");
        }
    }
    private static void cancel(AtomicReference<LimitedBody> ref) {var body=ref.get();if(body!=null)body.cancel();}

    static final class LimitedBody implements HttpResponse.BodySubscriber<MaxApiClient.DownloadedQuestionnaireAttachment> {
        private final CompletableFuture<MaxApiClient.DownloadedQuestionnaireAttachment> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private long expected=-1;
        private String contentType;
        private boolean done;
        LimitedBody(HttpResponse.ResponseInfo info) {
            try {
                if(info.statusCode()!=200)throw new IOException("Invalid MAX scoped media status");
                var lengths=info.headers().allValues("Content-Length");
                if(lengths.size()>1)throw new IOException("Ambiguous MAX scoped media length");
                if(!lengths.isEmpty()) {
                    if(!lengths.get(0).matches("[0-9]{1,19}"))throw new IOException("Invalid MAX scoped media length");
                    expected=Long.parseLong(lengths.get(0));
                    if(expected<1 || expected>QuestionnaireMedia.MAX_BYTES){failSize();return;}
                }
                contentType=info.headers().firstValue("Content-Type").orElse(null);
                if(contentType!=null && contentType.length()>255)throw new IOException("Invalid MAX scoped media type");
            } catch(Exception invalid) {fail();}
        }
        @Override public CompletionStage<MaxApiClient.DownloadedQuestionnaireAttachment> getBody() {return result;}
        @Override public synchronized void onSubscribe(Flow.Subscription value) {
            if(done || subscription!=null){value.cancel();return;}
            subscription=value;value.request(1);
        }
        @Override public synchronized void onNext(List<ByteBuffer> chunks) {
            if(done)return;
            for(var chunk:chunks) {
                if(chunk.remaining()>QuestionnaireMedia.MAX_BYTES-bytes.size()){failSize();return;}
                if(expected>=0 && chunk.remaining()>expected-bytes.size()){fail();return;}
                byte[] buffer=new byte[Math.min(8192,chunk.remaining())];
                while(chunk.hasRemaining()){int n=Math.min(buffer.length,chunk.remaining());chunk.get(buffer,0,n);bytes.write(buffer,0,n);}
            }
            if(subscription!=null)subscription.request(1);
        }
        @Override public synchronized void onError(Throwable failure) {fail();}
        @Override public synchronized void onComplete() {
            if(done)return;
            if(bytes.size()==0){failSize();return;}
            if(expected>=0 && expected!=bytes.size()){fail();return;}
            done=true;result.complete(new MaxApiClient.DownloadedQuestionnaireAttachment(bytes.toByteArray(),contentType));bytes.reset();
        }
        synchronized void cancel() {fail();}
        private void failSize() {
            if(done)return;done=true;bytes.reset();
            result.completeExceptionally(new com.example.supportbot.service.AttachmentService.MediaSizeException());
            if(subscription!=null)subscription.cancel();
        }
        private void fail() {
            if(done)return;done=true;bytes.reset();
            result.completeExceptionally(new IOException("Invalid or unavailable MAX scoped media bytes"));
            if(subscription!=null)subscription.cancel();
        }
    }
}
