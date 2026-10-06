package com.jobproof.modules.aigateway.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.application.AiGatewayException;
import com.jobproof.modules.aigateway.port.AiTransportPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class JdkAiTransportAdapter implements AiTransportPort {
    private static final int MAX_RESPONSE_BYTES=2*1024*1024;
    private static final int MAX_SSE_LINE_BYTES=256*1024;
    private final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;
    public JdkAiTransportAdapter(ObjectMapper mapper){this.mapper=mapper;}

    @Override
    public TransportResponse exchange(URI uri,String method,Map<String,String> headers,JsonNode body,Duration timeout){
        RawTransportResponse response=exchangeRaw(uri,method,headers,body,timeout);
        try{
            JsonNode responseBody=response.body().isBlank()?mapper.createObjectNode():mapper.readTree(response.body());
            return new TransportResponse(response.status(),response.headers(),responseBody);
        }catch(AiGatewayException exception){throw exception;}
        catch(Exception exception){throw new AiGatewayException("AI HTTP request failed",exception,true,false);}
    }

    @Override
    public RawTransportResponse exchangeRaw(URI uri,String method,Map<String,String> headers,JsonNode body,Duration timeout){
        try{
            HttpRequest.Builder request=HttpRequest.newBuilder(uri).timeout(timeout);
            headers.forEach(request::header);
            if("GET".equalsIgnoreCase(method))request.GET();
            else request.method(method,HttpRequest.BodyPublishers.ofByteArray(body==null?new byte[0]:mapper.writeValueAsBytes(body)));
            HttpResponse<byte[]> response=client.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());
            if(response.body().length>MAX_RESPONSE_BYTES)throw new AiGatewayException("AI response exceeded 2 MiB",false,false);
            Map<String,String> responseHeaders=new LinkedHashMap<>();
            for(Map.Entry<String,List<String>> value:response.headers().map().entrySet())responseHeaders.put(value.getKey(),String.join(",",value.getValue()));
            return new RawTransportResponse(response.statusCode(),Map.copyOf(responseHeaders),new String(response.body(),java.nio.charset.StandardCharsets.UTF_8));
        }catch(AiGatewayException exception){throw exception;}
        catch(Exception exception){throw new AiGatewayException("AI HTTP request failed",exception,true,false);}
    }

    @Override
    public StreamTransportResponse exchangeStream(URI uri, String method, Map<String, String> headers,
            JsonNode body, Duration timeout, Consumer<String> dataConsumer) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout);
            headers.forEach(request::header);
            if ("GET".equalsIgnoreCase(method)) request.GET();
            else request.method(method, HttpRequest.BodyPublishers.ofByteArray(
                    body == null ? new byte[0] : mapper.writeValueAsBytes(body)));
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            Map<String, String> responseHeaders = headers(response);
            try (InputStream input = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    byte[] error = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                    if (error.length > MAX_RESPONSE_BYTES) {
                        throw new AiGatewayException("AI response exceeded 2 MiB", false, false);
                    }
                    return new StreamTransportResponse(response.statusCode(), responseHeaders,
                            new String(error, StandardCharsets.UTF_8));
                }
                consumeSse(input, dataConsumer);
                return new StreamTransportResponse(response.statusCode(), responseHeaders, "");
            }
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiGatewayException("AI streaming request failed", exception, true, false);
        }
    }

    private static void consumeSse(InputStream input, Consumer<String> dataConsumer) throws Exception {
        try (BufferedInputStream buffered = new BufferedInputStream(input)) {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int total = 0;
            int value;
            while ((value = buffered.read()) != -1) {
                total++;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new AiGatewayException("AI response exceeded 2 MiB", false, false);
                }
                if (value == '\n') {
                    consumeSseLine(line, dataConsumer);
                    line.reset();
                    continue;
                }
                if (value != '\r') {
                    if (line.size() >= MAX_SSE_LINE_BYTES) {
                        throw new AiGatewayException("AI SSE line exceeded 256 KiB", false, false);
                    }
                    line.write(value);
                }
            }
            if (line.size() > 0) consumeSseLine(line, dataConsumer);
        }
    }

    private static void consumeSseLine(ByteArrayOutputStream line, Consumer<String> dataConsumer) {
        String value = line.toString(StandardCharsets.UTF_8);
        if (!value.startsWith("data:")) return;
        String data = value.substring(5);
        if (data.startsWith(" ")) data = data.substring(1);
        dataConsumer.accept(data);
    }

    private static Map<String, String> headers(HttpResponse<?> response) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> value : response.headers().map().entrySet()) {
            values.put(value.getKey(), String.join(",", value.getValue()));
        }
        return Map.copyOf(values);
    }
}
