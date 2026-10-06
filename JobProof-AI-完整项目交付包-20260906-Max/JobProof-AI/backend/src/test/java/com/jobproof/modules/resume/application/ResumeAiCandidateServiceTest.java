package com.jobproof.modules.resume.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.infrastructure.queue.OutboxService;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Response;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Usage;
import com.jobproof.modules.aigateway.domain.AiProtocol;
import com.jobproof.modules.aigateway.port.AiChannelPort;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.career.application.CareerLibraryService;
import com.jobproof.modules.career.application.CareerLibraryService.EvidenceSnapshot;
import com.jobproof.modules.career.application.CareerLibraryService.SourceRef;
import com.jobproof.modules.resume.infra.ResumeCandidateEntity;
import com.jobproof.modules.resume.infra.ResumeCandidateJpaRepository;
import com.jobproof.modules.resume.infra.ResumeMasterEntity;
import com.jobproof.modules.resume.infra.ResumeMasterJpaRepository;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.time.ClockPort;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ResumeAiCandidateServiceTest {
    private final ResumeMasterJpaRepository masters=mock(ResumeMasterJpaRepository.class);
    private final ResumeCandidateJpaRepository candidates=mock(ResumeCandidateJpaRepository.class);
    private final AiGatewayService gateway=mock(AiGatewayService.class);
    private final AiChannelPort channels=mock(AiChannelPort.class);private final OutboxService outbox=mock(OutboxService.class);
    private final AuditService audit=mock(AuditService.class);private final ClockPort clock=mock(ClockPort.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);private final CareerLibraryService careerLibrary=mock(CareerLibraryService.class);
    private final ObjectMapper mapper=new ObjectMapper();private ResumeAiCandidateService service;private ResumeMasterEntity master;

    @BeforeEach void setUp()throws Exception{
        master=new ResumeMasterEntity();master.setId("resume-1");master.setAccountId("account-1");master.setTitle("技术简历");master.setStatus("DRAFT");master.setExperienceJson(mapper.writeValueAsString("在平台开发接口并处理线上问题"));master.setKeyOutcomesJson("[]");master.setVersionNo(3);master.setCreatedAt(Instant.parse("2026-08-22T00:00:00Z"));master.setUpdatedAt(master.getCreatedAt());
        when(masters.findById("resume-1")).thenReturn(Optional.of(master));when(clock.now()).thenReturn(Instant.parse("2026-08-22T01:00:00Z"));
        when(channels.findEligible("account-1","qwen-plus")).thenReturn(List.of(new Channel("channel-1",AiProtocol.OPENAI_CHAT,URI.create("https://api.example/v1"),"cipher",0,100,true)));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(),any())).thenReturn(1);
        when(careerLibrary.evidenceSnapshot(eq("account-1"),anyString())).thenReturn(new EvidenceSnapshot(5,
                List.of(new SourceRef("record-1","PROJECT","招聘平台","招聘平台 Java 后端开发与接口维护"))));
        service=new ResumeAiCandidateService(masters,candidates,gateway,channels,outbox,audit,clock,mapper,true,"qwen-plus",null,jdbc,careerLibrary);
    }

    @Test void validModelRewriteCreatesPendingCandidateWithoutOverwritingFormalField(){
        when(gateway.execute(any(),any())).thenReturn(response("""
                {"candidates":[{"fieldKey":"EXPERIENCE","proposedValue":"负责平台接口开发与线上问题处理","reason":"压缩表述并突出职责","sourceFacts":[{"source":"resume.experience","quote":"平台开发接口"},{"source":"resume.experience","quote":"线上问题"}]}]}
                """));AtomicReference<ResumeCandidateEntity> saved=new AtomicReference<>();when(candidates.save(any())).thenAnswer(invocation->{saved.set(invocation.getArgument(0));return invocation.getArgument(0);});
        ResumeService.CandidateView view=service.generate(new CurrentAccount("account-1","user@example.com","SEEKER","session"),"resume-1",new ResumeAiCandidateService.GenerateCommand("EXPERIENCE","POLISH",null,3));
        assertThat(view.status()).isEqualTo("PENDING");assertThat(view.candidateSource()).isEqualTo("AI_MODEL");assertThat(view.reason()).contains("突出职责");assertThat(view.diff().path("before").asText()).contains("平台开发接口");assertThat(view.sourceFacts()).hasSize(2);
        assertThat(saved.get().getGenerationMetadataJson()).contains("channel-1","qwen-plus");assertThat(master.getExperienceJson()).contains("在平台开发接口");assertThat(master.getStatus()).isEqualTo("PENDING_CONFIRMATION");assertThat(master.getVersionNo()).isEqualTo(4);
    }

    @Test void unsupportedNumberRejectsWholeCandidateAndLeavesMasterUntouched(){
        when(gateway.execute(any(),any())).thenReturn(response("""
                {"candidates":[{"fieldKey":"EXPERIENCE","proposedValue":"负责平台接口开发，性能提升 30%","reason":"突出成果","sourceFacts":[{"source":"resume.experience","quote":"平台开发接口"}]}]}
                """));
        assertThatThrownBy(()->service.generate(new CurrentAccount("account-1","user@example.com","SEEKER","session"),"resume-1",new ResumeAiCandidateService.GenerateCommand("EXPERIENCE","POLISH",null,3))).isInstanceOf(AppException.class).extracting(value->((AppException)value).reason()).isEqualTo("AI_UNSUPPORTED_FACT");
        verify(candidates,never()).save(any());assertThat(master.getStatus()).isEqualTo("DRAFT");assertThat(master.getVersionNo()).isEqualTo(3);
    }

    @Test void enabledCareerLibraryEvidenceIsTraceableAndVersioned(){
        when(gateway.execute(any(),any())).thenReturn(response("""
                {"candidates":[{"fieldKey":"EXPERIENCE","proposedValue":"负责招聘平台 Java 后端开发与接口维护","reason":"结合已确认项目资料补充职责","sourceFacts":[{"source":"careerLibrary.record.record-1","quote":"招聘平台 Java 后端开发与接口维护"}]}]}
                """));
        AtomicReference<ResumeCandidateEntity> saved=new AtomicReference<>();when(candidates.save(any())).thenAnswer(invocation->{saved.set(invocation.getArgument(0));return invocation.getArgument(0);});
        ResumeService.CandidateView view=service.generate(new CurrentAccount("account-1","user@example.com","SEEKER","session"),"resume-1",new ResumeAiCandidateService.GenerateCommand("EXPERIENCE","POLISH",null,3));
        assertThat(view.careerLibrarySnapshotVersion()).isEqualTo(5);
        assertThat(view.sourceRefs().get(0).path("id").asText()).isEqualTo("record-1");
        assertThat(view.sourceStale()).isFalse();
        assertThat(saved.get().getCareerLibrarySourcesJson()).contains("招聘平台","record-1");
    }

    private Response response(String text){return new Response("request-1","channel-1","qwen-plus",text,Usage.of(100,50),mapper.createObjectNode());}
}
