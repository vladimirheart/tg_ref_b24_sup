package com.example.panel.config;

import jakarta.servlet.MultipartConfigElement;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import static org.assertj.core.api.Assertions.assertThat;

/** Real production properties + embedded Tomcat, no application/database/provider beans. */
class UnlimitedMultipartTest {
    @TempDir Path temp;
    @Configuration(proxyBeanMethods=false)
    @Import({ServletWebServerFactoryAutoConfiguration.class,DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,MultipartAutoConfiguration.class,HttpMessageConvertersAutoConfiguration.class,
        EmbeddedWebServerFactoryCustomizerAutoConfiguration.class})
    static class Fixture {
        @Bean Endpoint endpoint(){return new Endpoint();}
    }
    @RestController static class Endpoint {
        @PostMapping("/fixture/upload")
        Map<String,Long> upload(@RequestParam("file") MultipartFile file,@RequestParam("comment") String comment) throws Exception {
            try(var input=file.getInputStream()){
                return Map.of("size",file.getSize(),"received",input.transferTo(OutputStream.nullOutputStream()),"commentLength",(long)comment.length());
            }
        }
    }
    @Test void fullPayloadAboveAllFormerLimitsReachesTheReceivingEndpoint() throws Exception {
        long bytes=61L*1024*1024;int fieldBytes=3*1024*1024;
        Path file=temp.resolve("large.bin");
        try(var content=new RandomAccessFile(file.toFile(),"rw")){content.setLength(bytes);}
        SpringApplication app=new SpringApplication(Fixture.class);
        try(var context=(ServletWebServerApplicationContext)app.run("--server.port=0","--server.address=127.0.0.1",
                "--spring.main.banner-mode=off","--logging.config=classpath:upload-fixture-logback.xml",
                "--logging.level.root=WARN")){
            var config=context.getBean(MultipartConfigElement.class);
            assertThat(config.getMaxFileSize()).isEqualTo(-1);
            assertThat(config.getMaxRequestSize()).isEqualTo(-1);
            var connector=((TomcatWebServer)context.getWebServer()).getTomcat().getConnector();
            assertThat(connector.getMaxPostSize()).isEqualTo(-1);
            assertThat(((org.apache.coyote.http11.AbstractHttp11Protocol<?>)connector.getProtocolHandler()).getMaxSwallowSize()).isEqualTo(-1);
            String boundary="r332-fixture";
            byte[] prefix=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"comment\"\r\n\r\n"
                +"x".repeat(fieldBytes)+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"large.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+context.getWebServer().getPort()+"/fixture/upload"))
                .header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),
                    HttpRequest.BodyPublishers.ofFile(file),HttpRequest.BodyPublishers.ofByteArray(suffix))).build();
            var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            var result=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
            assertThat(result.path("size").asLong()).isEqualTo(bytes);
            assertThat(result.path("received").asLong()).isEqualTo(bytes);
            assertThat(result.path("commentLength").asInt()).isEqualTo(fieldBytes);
        }
    }
}
