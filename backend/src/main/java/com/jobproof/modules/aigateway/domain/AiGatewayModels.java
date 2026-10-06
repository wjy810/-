package com.jobproof.modules.aigateway.domain;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI; import java.util.*;
public final class AiGatewayModels {
 private AiGatewayModels() {}
 public record Message(String role,String content) {}
 public record Request(String model,List<Message> messages,boolean stream,Map<String,JsonNode> options) { public Request { messages=messages==null?List.of():List.copyOf(messages); options=options==null?Map.of():Map.copyOf(options); } }
 public record Usage(long inputTokens,long outputTokens,long totalTokens) { public static Usage of(long i,long o){return new Usage(i,o,i+o);} }
 public record Response(String id,String channelId,String model,String text,Usage usage,JsonNode raw) {}
 public record Channel(String id,AiProtocol protocol,URI baseUri,String encryptedApiKey,String providerModel,
   int healthPriority,int weight,boolean enabled) {
  public Channel { providerModel=providerModel==null||providerModel.isBlank()?null:providerModel.trim(); }
  public Channel(String id,AiProtocol protocol,URI baseUri,String encryptedApiKey,int healthPriority,int weight,boolean enabled) {
   this(id,protocol,baseUri,encryptedApiKey,null,healthPriority,weight,enabled);
  }
  public String resolvedModel(String platformModel){return providerModel==null?platformModel:providerModel;}
 }
 public record Detection(boolean reachable,boolean authenticated,List<String> models,String detail) { public Detection { models=models==null?List.of():List.copyOf(models); } }
 public record CapabilityDetection(String normalizedBaseUrl,List<String> models,String testedModel,
   boolean authenticated,boolean modelDiscovery,boolean plainResponse,boolean streamingResponse,
   boolean structuredResponse,String failureCode,String detail) {
  public CapabilityDetection { models=models==null?List.of():List.copyOf(models); }
  public boolean passed(){return authenticated&&modelDiscovery&&plainResponse&&streamingResponse&&structuredResponse;}
 }
}
