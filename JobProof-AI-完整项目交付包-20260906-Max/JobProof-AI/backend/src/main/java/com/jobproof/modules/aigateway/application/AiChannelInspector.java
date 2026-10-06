package com.jobproof.modules.aigateway.application;
import com.fasterxml.jackson.databind.*;import com.jobproof.modules.aigateway.adapter.*;import com.jobproof.modules.aigateway.domain.AiGatewayModels.*;import com.jobproof.modules.aigateway.port.AiTransportPort;import com.jobproof.modules.aigateway.security.*;import java.net.URI;import java.time.Duration;import java.util.*;
public final class AiChannelInspector {
    private final AiTransportPort transport;
    private final ApiKeyCipher cipher;
    private final SsrfGuard ssrf;
    private final ObjectMapper json;

    public AiChannelInspector(AiTransportPort transport, ApiKeyCipher cipher, SsrfGuard ssrf, ObjectMapper json) {
        this.transport = transport;
        this.cipher = cipher;
        this.ssrf = ssrf;
        this.json = json;
    }

    public Detection inspect(Channel channel) {
        try {
            ssrf.validate(channel.baseUri());
            AiProtocolAdapter adapter = switch (channel.protocol()) {
                case OPENAI_CHAT -> new ProtocolAdapters.OpenAiChat(json);
                case OPENAI_RESPONSES -> new ProtocolAdapters.OpenAiResponses(json);
                case ANTHROPIC_MESSAGES -> new ProtocolAdapters.AnthropicMessages(json);
            };
            var uri = adapter.modelsUri(channel.baseUri());
            ssrf.validate(uri);
            var response = transport.exchange(uri, "GET",
                adapter.authHeaders(cipher.decrypt(channel.encryptedApiKey())), null, Duration.ofSeconds(10));
            if (response.status() == 401 || response.status() == 403) {
                return new Detection(true, false, List.of(), "Authentication rejected");
            }
            if (response.status() < 200 || response.status() >= 300) {
                return new Detection(false, false, List.of(), "HTTP " + response.status());
            }
            return new Detection(true, true, models(response.body()), "OK");
        } catch (RuntimeException e) {
            return new Detection(false, false, List.of(), e.getMessage());
        }
    }

    public CapabilityDetection inspectCapabilities(Channel channel,String requestedModel) {
        List<URI> bases=baseCandidates(channel.baseUri());
        String last="No compatible endpoint detected";
        for(URI base:bases){
            try{
                Channel candidate=new Channel(channel.id(),channel.protocol(),base,channel.encryptedApiKey(),
                        channel.healthPriority(),channel.weight(),channel.enabled());
                Detection discovery=inspect(candidate);
                if(!discovery.authenticated()){last=discovery.detail();continue;}
                if(discovery.models().isEmpty()){
                    return new CapabilityDetection(base.toString(),List.of(),null,true,false,false,false,false,
                            "MODEL_DISCOVERY_EMPTY","模型发现成功但没有返回可用模型");
                }
                String model=requestedModel==null||requestedModel.isBlank()?discovery.models().get(0):requestedModel.trim();
                if(!discovery.models().contains(model)){
                    return new CapabilityDetection(base.toString(),discovery.models(),model,true,true,false,false,false,
                            "MODEL_NOT_DISCOVERED","指定模型不在供应商发现列表中");
                }
                AiProtocolAdapter adapter=adapter(channel.protocol());
                String key=cipher.decrypt(channel.encryptedApiKey());
                Request plain=new Request(model,List.of(new Message("user","Reply with exactly OK")),false,Map.of());
                var plainOutgoing=adapter.adapt(base,key,plain);ssrf.validate(plainOutgoing.uri());
                var plainHttp=transport.exchange(plainOutgoing.uri(),"POST",plainOutgoing.headers(),plainOutgoing.body(),Duration.ofSeconds(30));
                boolean plainPassed=plainHttp.status()>=200&&plainHttp.status()<300&&!adapter.parse(plainHttp.body()).text().isBlank();
                if(!plainPassed)return failed(base,discovery.models(),model,true,true,false,false,false,"PLAIN_RESPONSE_FAILED","普通响应测试失败");

                Request stream=new Request(model,List.of(new Message("user","Reply with exactly OK")),true,Map.of());
                var streamOutgoing=adapter.adapt(base,key,stream);ssrf.validate(streamOutgoing.uri());
                var streamHttp=transport.exchangeRaw(streamOutgoing.uri(),"POST",streamOutgoing.headers(),streamOutgoing.body(),Duration.ofSeconds(30));
                boolean streamPassed=streamHttp.status()>=200&&streamHttp.status()<300
                        &&streamHttp.body().contains("data:")&&streamHttp.body().contains("[DONE]");
                if(!streamPassed)return failed(base,discovery.models(),model,true,true,true,false,false,"STREAM_RESPONSE_FAILED","流式响应未返回规范 SSE 数据和 [DONE]");

                Request structured=new Request(model,List.of(new Message("user","Return only this JSON object: {\"ok\":true}")),false,Map.of());
                var structuredOutgoing=adapter.adapt(base,key,structured);ssrf.validate(structuredOutgoing.uri());
                var structuredHttp=transport.exchange(structuredOutgoing.uri(),"POST",structuredOutgoing.headers(),structuredOutgoing.body(),Duration.ofSeconds(30));
                String structuredText=adapter.parse(structuredHttp.body()).text().trim().replace("```json","").replace("```","").trim();
                boolean structuredPassed=structuredHttp.status()>=200&&structuredHttp.status()<300
                        &&json.readTree(structuredText).path("ok").asBoolean(false);
                if(!structuredPassed)return failed(base,discovery.models(),model,true,true,true,true,false,"STRUCTURED_RESPONSE_FAILED","结构化输出测试失败");
                return new CapabilityDetection(base.toString(),discovery.models(),model,true,true,true,true,true,null,"全部能力测试通过");
            }catch(Exception exception){last=exception.getMessage();}
        }
        return new CapabilityDetection(channel.baseUri().toString(),List.of(),requestedModel,false,false,false,false,false,
                "ENDPOINT_DETECTION_FAILED",last);
    }

    private CapabilityDetection failed(URI base,List<String> models,String model,boolean auth,boolean discovery,
            boolean plain,boolean stream,boolean structured,String code,String detail){
        return new CapabilityDetection(base.toString(),models,model,auth,discovery,plain,stream,structured,code,detail);
    }

    private AiProtocolAdapter adapter(com.jobproof.modules.aigateway.domain.AiProtocol protocol){
        return switch(protocol){
            case OPENAI_CHAT -> new ProtocolAdapters.OpenAiChat(json);
            case OPENAI_RESPONSES -> new ProtocolAdapters.OpenAiResponses(json);
            case ANTHROPIC_MESSAGES -> new ProtocolAdapters.AnthropicMessages(json);
        };
    }

    private static List<URI> baseCandidates(URI value){
        String raw=value.toString().replaceAll("/+$","");
        if(raw.endsWith("/v1"))return List.of(URI.create(raw));
        return List.of(URI.create(raw+"/v1"),URI.create(raw));
    }

    private List<String> models(JsonNode body) {
        List<String> result = new ArrayList<>();
        JsonNode data = body.path("data");
        if (!data.isArray()) {
            data = body.path("models");
        }
        if (data.isArray()) {
            for (JsonNode item : data) {
                String id = item.isTextual() ? item.asText() : item.path("id").asText();
                if (!id.isBlank()) {
                    result.add(id);
                }
            }
        }
        return List.copyOf(result);
    }
}
