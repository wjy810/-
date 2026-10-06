package com.jobproof.modules.aigateway.adapter;
import com.fasterxml.jackson.databind.*; import com.fasterxml.jackson.databind.node.*; import com.jobproof.modules.aigateway.domain.AiGatewayModels.*; import java.net.URI; import java.util.Map;
public final class ProtocolAdapters {
 private ProtocolAdapters(){}
 public abstract static class Base implements AiProtocolAdapter { protected final ObjectMapper json;protected Base(ObjectMapper j){json=j;}protected ObjectNode common(Request r){ObjectNode b=json.createObjectNode().put("model",r.model()).put("stream",r.stream());r.options().forEach(b::set);return b;}protected ArrayNode messages(Request r){ArrayNode a=json.createArrayNode();for(Message m:r.messages())a.addObject().put("role",m.role()).put("content",m.content());return a;}protected URI endpoint(URI b,String p){String s=b.toString();return URI.create((s.endsWith("/")?s:s+"/")+p);}public URI modelsUri(URI b){return endpoint(b,"models");}public Map<String,String> authHeaders(String k){return Map.of("Authorization","Bearer "+k,"Content-Type","application/json");}}
 public static final class OpenAiChat extends Base {
  public OpenAiChat(ObjectMapper j){super(j);}
  public AdaptedRequest adapt(URI b,String k,Request r){ObjectNode x=common(r);x.set("messages",messages(r));return new AdaptedRequest(endpoint(b,"chat/completions"),authHeaders(k),x);}
  public Response parse(JsonNode b){JsonNode u=b.path("usage");long i=u.path("prompt_tokens").asLong(),o=u.path("completion_tokens").asLong();return new Response(b.path("id").asText(),null,b.path("model").asText(),b.path("choices").path(0).path("message").path("content").asText(),new Usage(i,o,u.path("total_tokens").asLong(i+o)),b);}
  public StreamEvent parseStreamData(String data){
   if("[DONE]".equals(data))return new StreamEvent(null,null,"",null,true,null);
   try{
    JsonNode b=json.readTree(data),u=b.path("usage");
    Usage usage=u.isMissingNode()||u.isNull()?null:new Usage(u.path("prompt_tokens").asLong(),u.path("completion_tokens").asLong(),u.path("total_tokens").asLong());
    String delta=b.path("choices").path(0).path("delta").path("content").asText("");
    return new StreamEvent(b.path("id").asText(null),b.path("model").asText(null),delta,usage,false,b);
   }catch(Exception exception){throw new com.jobproof.modules.aigateway.application.AiGatewayException("Malformed OpenAI SSE data",exception,false,false);}
  }
 }
 public static final class OpenAiResponses extends Base {public OpenAiResponses(ObjectMapper j){super(j);}public AdaptedRequest adapt(URI b,String k,Request r){ObjectNode x=common(r);x.set("input",messages(r));return new AdaptedRequest(endpoint(b,"responses"),authHeaders(k),x);}public Response parse(JsonNode b){JsonNode u=b.path("usage");long i=u.path("input_tokens").asLong(),o=u.path("output_tokens").asLong();String t=b.path("output_text").asText();if(t.isEmpty())t=b.path("output").path(0).path("content").path(0).path("text").asText();return new Response(b.path("id").asText(),null,b.path("model").asText(),t,new Usage(i,o,u.path("total_tokens").asLong(i+o)),b);}}
 public static final class AnthropicMessages extends Base {public AnthropicMessages(ObjectMapper j){super(j);}public AdaptedRequest adapt(URI b,String k,Request r){ObjectNode x=common(r);x.set("messages",messages(r));if(!x.has("max_tokens"))x.put("max_tokens",1024);return new AdaptedRequest(endpoint(b,"messages"),authHeaders(k),x);}public Map<String,String> authHeaders(String k){return Map.of("x-api-key",k,"anthropic-version","2023-06-01","Content-Type","application/json");}public Response parse(JsonNode b){JsonNode u=b.path("usage");long i=u.path("input_tokens").asLong(),o=u.path("output_tokens").asLong();return new Response(b.path("id").asText(),null,b.path("model").asText(),b.path("content").path(0).path("text").asText(),Usage.of(i,o),b);}}
}
