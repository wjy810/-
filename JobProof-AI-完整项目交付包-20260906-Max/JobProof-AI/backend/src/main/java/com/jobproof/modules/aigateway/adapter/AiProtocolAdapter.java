package com.jobproof.modules.aigateway.adapter;
import com.fasterxml.jackson.databind.JsonNode; import com.jobproof.modules.aigateway.domain.AiGatewayModels.*; import java.net.URI; import java.util.Map;
public interface AiProtocolAdapter {
 AdaptedRequest adapt(URI base,String key,Request request); Response parse(JsonNode body);
 default StreamEvent parseStreamData(String data){throw new UnsupportedOperationException("Protocol streaming is not supported");}
 URI modelsUri(URI base); Map<String,String> authHeaders(String key);
 record AdaptedRequest(URI uri,Map<String,String> headers,JsonNode body){}
 record StreamEvent(String id,String model,String delta,Usage usage,boolean done,JsonNode raw){}
}
