package com.jobproof.modules.aigateway.port;
import com.fasterxml.jackson.databind.JsonNode; import java.net.URI; import java.time.Duration; import java.util.Map; import java.util.function.Consumer;
public interface AiTransportPort {
 TransportResponse exchange(URI uri,String method,Map<String,String> headers,JsonNode body,Duration timeout);
 default RawTransportResponse exchangeRaw(URI uri,String method,Map<String,String> headers,JsonNode body,Duration timeout){
  TransportResponse response=exchange(uri,method,headers,body,timeout);
  return new RawTransportResponse(response.status(),response.headers(),response.body()==null?"":response.body().toString());
 }
 default StreamTransportResponse exchangeStream(URI uri,String method,Map<String,String> headers,JsonNode body,
   Duration timeout,Consumer<String> dataConsumer){throw new UnsupportedOperationException("Streaming is not supported");}
 record TransportResponse(int status,Map<String,String> headers,JsonNode body){}
 record RawTransportResponse(int status,Map<String,String> headers,String body){}
 record StreamTransportResponse(int status,Map<String,String> headers,String errorBody){}
}
