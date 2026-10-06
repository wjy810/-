package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.infrastructure.queue.OutboxService;
import com.jobproof.modules.airesume.application.AiQuotaService;
import com.jobproof.modules.aigateway.application.AiGatewayException;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Message;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Request;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Response;
import com.jobproof.modules.aigateway.port.AiChannelPort;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.career.application.CareerLibraryService;
import com.jobproof.modules.career.application.CareerLibraryService.EvidenceSnapshot;
import com.jobproof.modules.career.application.CareerLibraryService.SourceRef;
import com.jobproof.modules.resume.domain.ResumeCandidateStatus;
import com.jobproof.modules.resume.domain.ResumeEventTypes;
import com.jobproof.modules.resume.domain.ResumeFieldKey;
import com.jobproof.modules.resume.domain.ResumeMasterPolicy;
import com.jobproof.modules.resume.domain.ResumeMasterStatus;
import com.jobproof.modules.resume.infra.ResumeCandidateEntity;
import com.jobproof.modules.resume.infra.ResumeCandidateJpaRepository;
import com.jobproof.modules.resume.infra.ResumeMasterEntity;
import com.jobproof.modules.resume.infra.ResumeMasterJpaRepository;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.concurrency.Versions;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeAiCandidateService {
    private static final Set<ResumeFieldKey> SUPPORTED=Set.of(ResumeFieldKey.SELF_INTRO,ResumeFieldKey.EXPERIENCE,ResumeFieldKey.PROJECTS);
    private static final Set<String> ACTIONS=Set.of("REFINE","POLISH");
    private static final Pattern NUMBER_OR_DATE=Pattern.compile("(?iu)(?:19|20)\\d{2}(?:[-/.年]\\d{1,2}(?:[-/.月]\\d{1,2}日?)?)?|(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?%?");
    private static final Pattern NAMED_FACT=Pattern.compile("[\\p{IsHan}A-Za-z0-9·&（）()]{2,32}(?:有限责任公司|股份有限公司|公司|大学|学院|学校|银行|医院|工程师|经理|教师|护士)");
    private static final String SYSTEM_PROMPT="""
            你是简历字段编辑器。只能重写用户提供的事实，不得补充示例事实、公司、岗位、日期、学校、数字或成果。
            仅输出一个 JSON 对象，不要 Markdown。格式：
            {"candidates":[{"fieldKey":"SELF_INTRO|EXPERIENCE|PROJECTS","proposedValue":"文本","reason":"简短理由","sourceFacts":[{"source":"resume.experience","quote":"输入中的原文片段"}]}]}
            sourceFacts 的 quote 必须逐字出现在输入 facts 中。每个字段只输出一项。没有足够事实时返回 {"candidates":[]}。
            """;

    private final ResumeMasterJpaRepository masters;private final ResumeCandidateJpaRepository candidates;
    private final AiGatewayService gateway;private final AiChannelPort channels;
    private final OutboxService outbox;private final AuditService audit;private final ClockPort clock;private final ObjectMapper mapper;
    private final boolean enabled;private final String defaultModel;
    private final AiQuotaService quota;private final JdbcTemplate jdbc;private final CareerLibraryService careerLibrary;
    @Autowired
    public ResumeAiCandidateService(ResumeMasterJpaRepository masters,ResumeCandidateJpaRepository candidates,
            AiGatewayService gateway,AiChannelPort channels,OutboxService outbox,AuditService audit,
            ClockPort clock,ObjectMapper mapper,@Value("${jobproof.ai.gateway.enabled:false}") boolean enabled,
            @Value("${jobproof.ai.resume-model:qwen-plus}") String defaultModel,AiQuotaService quota,JdbcTemplate jdbc,
            CareerLibraryService careerLibrary){this.masters=masters;this.candidates=candidates;this.gateway=gateway;this.channels=channels;this.outbox=outbox;this.audit=audit;this.clock=clock;this.mapper=mapper;this.enabled=enabled;this.defaultModel=defaultModel;this.quota=quota;this.jdbc=jdbc;this.careerLibrary=careerLibrary;}

    public ResumeAiCandidateService(ResumeMasterJpaRepository masters,ResumeCandidateJpaRepository candidates,
            AiGatewayService gateway,AiChannelPort channels,OutboxService outbox,AuditService audit,
            ClockPort clock,ObjectMapper mapper,boolean enabled,String defaultModel){this(masters,candidates,gateway,channels,outbox,audit,clock,mapper,enabled,defaultModel,null,null,null);}

    @Transactional(readOnly=true)
    public Availability availability(CurrentAccount current,String requestedModel){assertSeeker(current);String model=model(requestedModel);if(!enabled)return new Availability(false,model,"AI_GATEWAY_DISABLED");return channels.findEligible(current.accountId(),model).isEmpty()?new Availability(false,model,"AI_CHANNEL_UNAVAILABLE"):new Availability(true,model,null);}

    @Transactional
    public ResumeService.CandidateView generate(CurrentAccount current,String masterId,GenerateCommand command){
        assertSeeker(current);ResumeMasterEntity master=requireOwn(current.accountId(),masterId);Versions.assertExpected(command.expectedVersion(),master.getVersionNo());ResumeMasterPolicy.assertEditable(ResumeMasterStatus.parse(master.getStatus()));
        ResumeFieldKey field=ResumeFieldKey.parse(command.fieldKey());if(!SUPPORTED.contains(field))throw AppException.user("AI_RESUME_FIELD_UNSUPPORTED","AI 仅支持个人简介、经历要点和项目要点");
        String action=action(command.action());String model=model(command.model());ensureAvailable(current.accountId(),model);
        Map<String,String> facts=facts(master);String before=facts.get(fieldSource(field));if(before==null||before.isBlank())throw AppException.user("AI_SOURCE_FACTS_REQUIRED","该字段没有可供 AI 改写的用户事实");
        EvidenceSnapshot careerEvidence=careerEvidence(current.accountId(),masterId,field,action,before,facts);
        AiQuotaService.Reservation reservation=authorizeAndReserve(current.accountId(),masterId,field.name(),action);
        try{
            Response response=execute(current.accountId(),model,prompt(action,List.of(field),facts));List<ParsedCandidate> parsed=parse(response,List.of(field),facts,trustedText(facts));
            if(parsed.isEmpty())throw AppException.conflict("AI_NO_CANDIDATE","模型未返回可用候选，正式内容未改变");ParsedCandidate result=parsed.get(0);if(normalize(before).equals(normalize(result.value())))throw AppException.conflict("AI_NO_CHANGE","模型候选与原字段相同，未创建空变更");
            Instant now=clock.now();ResumeCandidateEntity entity=entity(current.accountId(),masterId,field,before,result,response,action,careerEvidence,now);candidates.save(entity);
            master.setStatus(ResumeMasterStatus.PENDING_CONFIRMATION.name());master.setVersionNo(master.getVersionNo()+1);master.setUpdatedAt(now);masters.save(master);
            outbox.enqueue(ResumeEventTypes.CANDIDATE_CREATED,Map.of("accountId",current.accountId(),"masterId",masterId,"candidateId",entity.getId()));
            audit.append(current.accountId(),"RESUME_AI_CANDIDATE_GENERATED","RESUME_CANDIDATE",entity.getId(),"field="+field.name()+" action="+action+" model="+response.model()+" channel="+response.channelId());
            if(reservation!=null)quota.settle(current.accountId(),reservation.id(),1);
            return view(entity);
        }catch(RuntimeException exception){if(reservation!=null)quota.release(current.accountId(),reservation.id());throw exception;}
    }

    private AiQuotaService.Reservation authorizeAndReserve(String accountId,String masterId,String field,String action){
        if(quota==null||jdbc==null)return null;
        Integer consent=jdbc.queryForObject("SELECT COUNT(*) FROM ai_user_consents WHERE account_id=? AND consent_type='AI_RESUME_WORKBENCH' AND status='GRANTED'",Integer.class,accountId);
        if(consent==null||consent==0)throw AppException.conflict("AI_CONSENT_REQUIRED","请先在 AI 简历工作台确认授权");
        return quota.reserve(accountId,"candidate:"+masterId+":"+field+":"+action+":"+Ids.newId(),"RESUME_CANDIDATE",1);
    }

    private Response execute(String accountId,String model,String prompt){try{ObjectNode temperature=mapper.createObjectNode();temperature.put("value",0.2);Map<String,JsonNode> options=Map.of("temperature",mapper.getNodeFactory().numberNode(0.2),"max_tokens",mapper.getNodeFactory().numberNode(1800));return gateway.execute(accountId,new Request(model,List.of(new Message("system",SYSTEM_PROMPT),new Message("user",prompt)),false,options));}catch(AiGatewayException exception){throw AppException.dependency("AI_MODEL_FAILED","AI 模型调用失败，未创建候选，也未改写正式内容");}}
    private String prompt(String action,List<ResumeFieldKey> fields,Map<String,String> facts){Map<String,Object> input=new LinkedHashMap<>();input.put("action",action);input.put("requestedFields",fields.stream().map(Enum::name).toList());input.put("facts",facts);return json(input);}
    private List<ParsedCandidate> parse(Response response,List<ResumeFieldKey> requested,Map<String,String> facts,String resumeText){try{String raw=response.text()==null?"":response.text().trim();if(raw.startsWith("```")){raw=raw.replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");}int start=raw.indexOf('{'),end=raw.lastIndexOf('}');if(start<0||end<start)throw new IllegalArgumentException();JsonNode root=mapper.readTree(raw.substring(start,end+1));JsonNode items=root.path("candidates");if(!items.isArray())throw new IllegalArgumentException();List<ParsedCandidate> result=new ArrayList<>();Set<ResumeFieldKey> allowed=Set.copyOf(requested);for(JsonNode item:items){ResumeFieldKey field=ResumeFieldKey.parse(item.path("fieldKey").asText());if(!allowed.contains(field)||result.stream().anyMatch(value->value.field()==field))throw new IllegalArgumentException();String value=item.path("proposedValue").asText().trim();String reason=item.path("reason").asText().trim();if(value.isEmpty()||value.length()>20000||reason.isEmpty()||reason.length()>2048)throw new IllegalArgumentException();List<Map<String,String>> citations=citations(item.path("sourceFacts"),facts);assertSupportedFacts(value,resumeText);result.add(new ParsedCandidate(field,value,reason,citations));}return List.copyOf(result);}catch(AppException exception){throw exception;}catch(Exception exception){throw AppException.dependency("AI_RESPONSE_INVALID","AI 返回格式无效，未创建候选，也未改写正式内容");}}
    private List<Map<String,String>> citations(JsonNode value,Map<String,String> facts){if(!value.isArray()||value.isEmpty())throw AppException.conflict("AI_SOURCE_CITATION_REQUIRED","AI 候选缺少来源事实引用");List<Map<String,String>> result=new ArrayList<>();for(JsonNode item:value){String source=item.isTextual()?"resume":item.path("source").asText("resume");String quote=item.isTextual()?item.asText():item.path("quote").asText();quote=quote.trim();String sourceValue=facts.get(source);if(quote.isEmpty()||quote.length()>500||sourceValue==null||!sourceValue.contains(quote))throw AppException.conflict("AI_SOURCE_CITATION_INVALID","AI 引用不是所标记输入事实的原文片段");result.add(Map.of("source",source,"quote",quote));}return List.copyOf(result);}
    private void assertSupportedFacts(String proposed,String resumeSource){for(Pattern pattern:List.of(NUMBER_OR_DATE,NAMED_FACT)){Matcher matcher=pattern.matcher(proposed);while(matcher.find()){String fact=matcher.group();if(!resumeSource.contains(fact))throw AppException.conflict("AI_UNSUPPORTED_FACT","AI 候选引入了输入事实不支持的公司、岗位、日期、学校或数字："+fact);}}}

    private ResumeCandidateEntity entity(String accountId,String masterId,ResumeFieldKey field,String before,ParsedCandidate result,Response response,String action,EvidenceSnapshot evidence,Instant now){ResumeCandidateEntity entity=new ResumeCandidateEntity();entity.setId(Ids.newId());entity.setAccountId(accountId);entity.setMasterId(masterId);entity.setFieldKey(field.name());entity.setProposedValueJson(json(result.value()));entity.setStatus(ResumeCandidateStatus.PENDING.name());entity.setCandidateSource("AI_MODEL");entity.setAiAction(action);entity.setReasonText(result.reason());entity.setDiffJson(json(Map.of("before",before,"after",result.value(),"changed",true)));entity.setSourceFactsJson(json(result.citations()));entity.setGenerationMetadataJson(json(Map.of("requestId",nullToEmpty(response.id()),"channelId",nullToEmpty(response.channelId()),"model",nullToEmpty(response.model()),"inputTokens",response.usage().inputTokens(),"outputTokens",response.usage().outputTokens(),"totalTokens",response.usage().totalTokens())));List<SourceRef> refs=usedCareerSources(result,evidence);if(!refs.isEmpty()){entity.setCareerLibrarySnapshotVersion(evidence.snapshotVersion());entity.setCareerLibrarySourcesJson(json(refs));}entity.setSourceStale(false);entity.setVersionNo(0);entity.setCreatedAt(now);return entity;}
    private ResumeService.CandidateView view(ResumeCandidateEntity entity){return new ResumeService.CandidateView(entity.getId(),entity.getMasterId(),entity.getFieldKey(),read(entity.getProposedValueJson()),entity.getStatus(),entity.getCandidateSource(),entity.getAiAction(),entity.getReasonText(),read(entity.getDiffJson()),read(entity.getSourceFactsJson()),read(entity.getGenerationMetadataJson()),entity.getCareerLibrarySnapshotVersion(),read(entity.getCareerLibrarySourcesJson()),entity.isSourceStale(),entity.getVersionNo(),entity.getCreatedAt(),entity.getDecidedAt());}
    private Map<String,String> facts(ResumeMasterEntity master){Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("title",master.getTitle());snapshot.put("education",master.getEducationJson());snapshot.put("experience",master.getExperienceJson());snapshot.put("projects",master.getProjectsJson());snapshot.put("skills",master.getSkillsJson());snapshot.put("certificates",master.getCertificatesJson());snapshot.put("selfIntro",master.getSelfIntro());return facts(snapshot);}
    private Map<String,String> facts(Map<String,Object> snapshot){Map<String,String> facts=new LinkedHashMap<>();for(String key:List.of("title","education","experience","projects","skills","certificates","selfIntro")){String value=plain(snapshot.get(key));if(!value.isBlank())facts.put("resume."+key,value);}return facts;}
    private EvidenceSnapshot careerEvidence(String accountId,String masterId,ResumeFieldKey field,String action,String before,Map<String,String> facts){if(jdbc==null||careerLibrary==null)return null;Integer enabled=jdbc.queryForObject("SELECT COUNT(*) FROM ai_resume_conversations c JOIN ai_resume_preferences p ON p.account_id=c.account_id AND p.conversation_id=c.id WHERE c.account_id=? AND c.master_id=? AND p.preference_key='CAREER_LIBRARY_EVIDENCE' AND LOWER(p.preference_value)='true'",Integer.class,accountId,masterId);if(enabled==null||enabled==0)return null;String query=String.join(" ",field.name(),action,before);EvidenceSnapshot evidence=careerLibrary.evidenceSnapshot(accountId,query);for(SourceRef source:evidence.sources())facts.put("careerLibrary.record."+source.id(),source.excerpt());return evidence;}
    private List<SourceRef> usedCareerSources(ParsedCandidate result,EvidenceSnapshot evidence){if(evidence==null||evidence.sources().isEmpty())return List.of();Set<String> cited=result.citations().stream().map(value->value.get("source")).collect(java.util.stream.Collectors.toSet());return evidence.sources().stream().filter(source->cited.contains("careerLibrary.record."+source.id())).toList();}
    private String trustedText(Map<String,String> facts){return facts.entrySet().stream().filter(entry->entry.getKey().startsWith("resume.")||entry.getKey().startsWith("careerLibrary.record.")).map(Map.Entry::getValue).collect(java.util.stream.Collectors.joining("\n"));}
    private void ensureAvailable(String accountId,String model){if(!enabled)throw AppException.conflict("AI_GATEWAY_DISABLED","AI 未配置，当前不能生成候选");if(channels.findEligible(accountId,model).isEmpty())throw AppException.conflict("AI_CHANNEL_UNAVAILABLE","没有可用 AI 渠道，当前不能生成候选");}
    private ResumeMasterEntity requireOwn(String accountId,String id){ResumeMasterEntity value=masters.findById(id).orElseThrow(()->AppException.user("RESUME_NOT_FOUND","简历主档不存在"));if(!accountId.equals(value.getAccountId()))throw AppException.forbidden("OBJECT_FORBIDDEN","不能访问他人的简历");return value;}
    private String model(String value){String result=value==null||value.isBlank()?defaultModel:value.trim();if(result.length()>128)throw AppException.user("AI_MODEL_INVALID","模型编码无效");return result;}
    private static String action(String value){String result=value==null?"":value.trim().toUpperCase(Locale.ROOT);if(!ACTIONS.contains(result))throw AppException.user("AI_ACTION_INVALID","AI 操作仅支持 REFINE 或 POLISH");return result;}
    private String plain(Object value){if(value==null)return "";String text=String.valueOf(value);try{JsonNode node=mapper.readTree(text);return node.isTextual()?node.asText():node.isNull()?"":node.toString();}catch(Exception ignored){return text;}}
    private JsonNode read(String value){try{return value==null?null:mapper.readTree(value);}catch(Exception exception){return null;}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception exception){throw new IllegalStateException(exception);}}
    private static String fieldSource(ResumeFieldKey field){return switch(field){case SELF_INTRO->"resume.selfIntro";case EXPERIENCE->"resume.experience";case PROJECTS->"resume.projects";default->throw new IllegalArgumentException();};}
    private static String normalize(String value){return value==null?"":value.replaceAll("\\s+","").toLowerCase(Locale.ROOT);}
    private static String nullToEmpty(String value){return value==null?"":value;}
    private static void assertSeeker(CurrentAccount current){if(current==null||!"SEEKER".equals(current.role()))throw AppException.forbidden("OPERATOR_NO_ORIGINAL","仅求职者可以使用简历 AI");}
    public record Availability(boolean available,String model,String reason){}
    public record GenerateCommand(String fieldKey,String action,String model,Integer expectedVersion){}
    private record ParsedCandidate(ResumeFieldKey field,String value,String reason,List<Map<String,String>> citations){}
}
