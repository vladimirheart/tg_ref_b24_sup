package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import com.example.supportbot.service.QuestionnaireMedia;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

/** Actual JDK subscriber/cancellation against loopback only; no CDN/provider traffic. */
class MaxQuestionnaireDownloadHttpTest {
    HttpServer server;
    ExecutorService executor;
    URI uri;
    final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        executor=Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"r283-loopback");t.setDaemon(true);return t;});
        server.setExecutor(executor);uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/bytes");
    }
    @AfterEach void close() {server.stop(0);executor.shutdownNow();}
    @Test void realChunkedResponseUsesTheBoundedSubscriber() throws Exception {
        server.createContext("/bytes",exchange->{try(exchange){exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write(new byte[]{1,2,3});}});server.start();
        assertThat(MaxQuestionnaireDownload.download(http,uri,Duration.ofSeconds(3)).bytes()).containsExactly((byte)1,(byte)2,(byte)3);
    }
    @Test void realChunkedOversizeNeverEscapesAsDownloadedBytes() throws Exception {
        server.createContext("/bytes",exchange->{try(exchange){exchange.sendResponseHeaders(200,0);byte[] chunk=new byte[8192];for(int n=0;n<=QuestionnaireMedia.MAX_BYTES;n+=chunk.length)exchange.getResponseBody().write(chunk);}catch(IOException cancelled){/* Expected upstream cancellation. */}});server.start();
        assertThatThrownBy(()->MaxQuestionnaireDownload.download(http,uri,Duration.ofSeconds(3))).isInstanceOf(IOException.class);
    }
    @Test void deadlineIncludesBodyAfterHeadersHaveAlreadyArrived() throws Exception {
        var bodyStarted=new CountDownLatch(1);var release=new CountDownLatch(1);
        server.createContext("/bytes",exchange->{try(exchange){exchange.sendResponseHeaders(200,8);exchange.getResponseBody().write(1);exchange.getResponseBody().flush();bodyStarted.countDown();release.await(4,TimeUnit.SECONDS);}catch(IOException cancelled){}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});server.start();
        try {
            assertThatThrownBy(()->MaxQuestionnaireDownload.download(http,uri,Duration.ofMillis(500))).isInstanceOf(IOException.class);
            assertThat(bodyStarted.getCount()).isZero();
        } finally {release.countDown();}
    }
}
