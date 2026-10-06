package com.jobproof.modules.careerplanning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.modules.aigateway.application.AiGatewayException;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Request;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Response;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Usage;
import com.jobproof.modules.airesume.application.AiQuotaService;
import com.jobproof.modules.airesume.application.AiQuotaService.Reservation;
import com.jobproof.shared.error.AppException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CareerPlanningAiServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void recommendationPromptKeepsLargeStructuredContextAndSourceRefsIntact() throws Exception {
        ObjectNode context = mapper.createObjectNode();
        context.put("padding", "x".repeat(36_000));
        context.putArray("allowedSourceRefs").add("PROFILE_ITEM:item-1");

        String prompt = CareerPlanningAiService.recommendationPrompt(context);
        String json = prompt.substring(prompt.indexOf('{'));

        assertThat(prompt).contains("PROFILE_ITEM:item-1");
        assertThat(mapper.readTree(json)).isEqualTo(context);
    }

    @Test
    void recommendationPolicyTreatsStudentProjectsAsEvidenceInsteadOfBlockingAdjacentDirections() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        String response = """
                {"status":"READY","recommendations":[
                  {"taxonomyNodeId":"j1","title":"后端开发","tier":"READY_NOW","fitSummary":"匹配","rationale":["课程项目"],"gaps":[],"sourceRefs":["PROFILE_ITEM:p1"]},
                  {"taxonomyNodeId":"j2","title":"Java 开发","tier":"AFTER_SMALL_GAP","fitSummary":"相邻方向","rationale":["Java 实践"],"gaps":["工程经验"],"sourceRefs":["PROFILE_ITEM:p1"]},
                  {"taxonomyNodeId":"j3","title":"测试开发","tier":"EXPLORATORY","fitSummary":"可探索","rationale":["自动化测试"],"gaps":["测试平台经验"],"sourceRefs":["PROFILE_ITEM:p1"]}
                ],"insufficientReasons":[]}
                """;
        when(gateway.execute(anyString(), any(Request.class)))
                .thenReturn(new Response("r1", "c1", "test-model", response,
                        Usage.of(20, 10), mapper.createObjectNode()));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");

        ObjectNode context = recommendationContext();
        service.generateRecommendations("account-1", "request-1", context);

        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(gateway).execute(anyString(), request.capture());
        assertThat(request.getValue().messages().get(0).content())
                .contains("课程项目、社团实践和个人作品都是有效事实")
                .contains("缺少正式实习只能列为能力缺口")
                .contains("至少 3 个相邻岗位");
    }

    @Test
    void recommendationResponseRepairsUnauthorizedSourceRefsBeforeQuotaSettlement() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        String invalid = recommendationResponse("PROFILE_ITEM:invented");
        String repaired = recommendationResponse("PROFILE_ITEM:p1");
        when(gateway.execute(anyString(), any(Request.class)))
                .thenReturn(new Response("r1", "c1", "test-model", invalid,
                        Usage.of(20, 10), mapper.createObjectNode()))
                .thenReturn(new Response("r2", "c1", "test-model", repaired,
                        Usage.of(20, 10), mapper.createObjectNode()));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");

        var result = service.generateRecommendations("account-1", "request-1", recommendationContext());

        assertThat(result.value().recommendations()).allSatisfy(item ->
                assertThat(item.sourceRefs()).containsExactly("PROFILE_ITEM:p1"));
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(gateway, times(2)).execute(anyString(), request.capture());
        assertThat(request.getAllValues().get(1).messages().get(3).content())
                .contains("allowedSourceRefs")
                .contains("原样复制");
    }

    @Test
    void recommendationUsesCanonicalTitleForAnAllowedTaxonomyNode() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        String response = """
                {"status":"READY","recommendations":[
                  {"taxonomyNodeId":"j1","title":"Java 后端工程师","tier":"READY_NOW","fitSummary":"匹配","rationale":["课程项目"],"gaps":[],"sourceRefs":["PROFILE_ITEM:p1"]},
                  {"taxonomyNodeId":"j2","title":"Java工程师","tier":"AFTER_SMALL_GAP","fitSummary":"相邻方向","rationale":["Java 实践"],"gaps":["工程经验"],"sourceRefs":["PROFILE_ITEM:p1"]},
                  {"taxonomyNodeId":"j3","title":"测试开发工程师","tier":"EXPLORATORY","fitSummary":"可探索","rationale":["自动化测试"],"gaps":["测试平台经验"],"sourceRefs":["PROFILE_ITEM:p1"]}
                ],"insufficientReasons":[]}
                """;
        when(gateway.execute(anyString(), any(Request.class)))
                .thenReturn(new Response("r1", "c1", "test-model", response,
                        Usage.of(20, 10), mapper.createObjectNode()));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");

        var result = service.generateRecommendations("account-1", "request-1", recommendationContext());

        assertThat(result.value().recommendations())
                .extracting(CareerPlanningAiService.AiRecommendation::title)
                .containsExactly("后端开发", "Java 开发", "测试开发");
        verify(gateway).execute(anyString(), any(Request.class));
    }

    @Test
    void rejectsCompleteCompensationVocabularyFromUserPreferences() {
        for (String term : new String[] {"待遇", "月薪", "年薪", "总包", "年包", "annual salary", "total compensation"}) {
            assertThatThrownBy(() -> CareerPlanningAiService.assertNoForbidden("我关注" + term))
                    .as("term %s", term)
                    .isInstanceOfSatisfying(AppException.class,
                            exception -> assertThat(exception.reason()).isEqualTo("CP_FORBIDDEN_PREFERENCE"));
        }
    }

    @Test
    void aiOutputPolicyViolationUsesStableBlockingReason() {
        assertThatThrownBy(() -> CareerPlanningAiService.assertNoForbiddenOutput("推荐理由包含年薪范围"))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("CAREER_OUTPUT_POLICY_BLOCKED"));
    }

    @Test
    void canvasGenerationOverridesTheGatewayDefaultTimeout() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        when(gateway.execute(anyString(), any(Request.class)))
                .thenThrow(new AiGatewayException("timeout", true, false));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");

        assertThatThrownBy(() -> service.generateCanvas("account-1", "request-1", mapper.createObjectNode()))
                .isInstanceOf(AppException.class);

        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(gateway).execute(anyString(), request.capture());
        assertThat(request.getValue().options().get("_request_timeout_seconds").asInt()).isGreaterThan(60);
    }

    @Test
    void canvasGenerationReportsValidationRepairAndFinalizationCheckpoints() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        String repaired = canvasResponse("GOAL:g1");
        when(gateway.execute(anyString(), any(Request.class)))
                .thenReturn(new Response("r1", "c1", "test-model", "{\"nodes\":[]}", Usage.of(10, 5), mapper.createObjectNode()))
                .thenReturn(new Response("r2", "c1", "test-model", repaired, Usage.of(20, 10), mapper.createObjectNode()));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");
        List<String> checkpoints = new ArrayList<>();

        ObjectNode context = mapper.createObjectNode();
        context.putArray("allowedSourceRefs").add("GOAL:g1");
        var result = service.generateCanvas("account-1", "request-1", context,
                (progress, checkpoint) -> checkpoints.add(progress + ":" + checkpoint));

        assertThat(result.value().nodes()).hasSize(15);
        assertThat(checkpoints).containsExactly(
                "62:VALIDATING_RESPONSE",
                "72:REPAIRING_RESPONSE",
                "84:VALIDATING_REPAIRED_RESPONSE",
                "88:FINALIZING_RESPONSE");
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(gateway, times(2)).execute(anyString(), request.capture());
        String repair = request.getAllValues().get(1).messages().get(3).content();
        assertThat(repair)
                .contains("能力节点数量必须为 14 至 60 个")
                .contains("严格按原 Schema 重写")
                .doesNotContain("恰好是 4 个 DOMAIN");
    }

    @Test
    void canvasResponseRepairsUnauthorizedSourceRefsBeforeQuotaSettlement() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        String invalid = canvasResponse("GOAL:invented");
        String repaired = canvasResponse("GOAL:g1");
        when(gateway.execute(anyString(), any(Request.class)))
                .thenReturn(new Response("r1", "c1", "test-model", invalid,
                        Usage.of(20, 10), mapper.createObjectNode()))
                .thenReturn(new Response("r2", "c1", "test-model", repaired,
                        Usage.of(20, 10), mapper.createObjectNode()));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");
        ObjectNode context = mapper.createObjectNode();
        context.putArray("allowedSourceRefs").add("GOAL:g1");

        var result = service.generateCanvas("account-1", "request-1", context);

        assertThat(result.value().nodes()).allSatisfy(node ->
                assertThat(node.sourceRefs()).containsExactly("GOAL:g1"));
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(gateway, times(2)).execute(anyString(), request.capture());
        assertThat(request.getAllValues().get(1).messages().get(3).content())
                .contains("allowedSourceRefs")
                .contains("严格按原 Schema 重写");
    }

    @Test
    void canvasRepairIdentifiesTheMissingParentAndReturnedKeys() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        String invalid = canvasResponse("GOAL:g1").replace(
                "\"key\":\"s1\",\"type\":\"SKILL\",\"title\":\"领域1技能\",\"parentKey\":\"d1\"",
                "\"key\":\"s1\",\"type\":\"SKILL\",\"title\":\"领域1技能\",\"parentKey\":\"missing_domain\"");
        String repaired = canvasResponse("GOAL:g1");
        when(gateway.execute(anyString(), any(Request.class)))
                .thenReturn(new Response("r1", "c1", "test-model", invalid,
                        Usage.of(20, 10), mapper.createObjectNode()))
                .thenReturn(new Response("r2", "c1", "test-model", repaired,
                        Usage.of(20, 10), mapper.createObjectNode()));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");
        ObjectNode context = mapper.createObjectNode();
        context.putArray("allowedSourceRefs").add("GOAL:g1");

        var result = service.generateCanvas("account-1", "request-1", context);

        assertThat(result.value().nodes()).hasSize(15);
        ArgumentCaptor<Request> requests = ArgumentCaptor.forClass(Request.class);
        verify(gateway, times(2)).execute(anyString(), requests.capture());
        assertThat(requests.getAllValues().get(1).messages().get(3).content())
                .contains("s1")
                .contains("missing_domain")
                .contains("d1")
                .contains("完整合法 key");
    }

    @Test
    void canvasCanonicalizesOnlyUniqueCaseInsensitiveReferencesWithoutRepair() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        String response = canvasResponse("GOAL:g1").replace(
                "\"key\":\"s1\",\"type\":\"SKILL\",\"title\":\"领域1技能\",\"parentKey\":\"d1\"",
                "\"key\":\"s1\",\"type\":\"SKILL\",\"title\":\"领域1技能\",\"parentKey\":\"D1\"");
        when(gateway.execute(anyString(), any(Request.class))).thenReturn(new Response(
                "r1", "c1", "test-model", response, Usage.of(20, 10), mapper.createObjectNode()));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");
        ObjectNode context = mapper.createObjectNode();
        context.putArray("allowedSourceRefs").add("GOAL:g1");

        var result = service.generateCanvas("account-1", "request-1", context);

        assertThat(result.value().nodes()).filteredOn(node -> "s1".equals(node.key()))
                .singleElement().extracting(CareerPlanningAiService.AiCanvasNode::parentKey).isEqualTo("d1");
        verify(gateway, times(1)).execute(anyString(), any(Request.class));
    }

    @Test
    void canvasProposalRepairReceivesTheExactAddFieldContract() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        String invalid = """
                {"items":[{"key":"p1","operation":"ADD","targetNodeId":null,
                  "nodeType":"TASK","title":"容器化交付练习","detail":{},
                  "reason":"补充验证任务","sourceRefs":["GOAL:g1"],"impactNodeIds":[]}]}
                """;
        String repaired = """
                {"items":[{"key":"p1","operation":"ADD","targetNodeId":null,
                  "parentNodeId":"node-1","nodeType":"TASK","title":"容器化交付练习",
                  "status":"NOT_STARTED","detail":{"summary":"完成容器化交付练习"},
                  "reason":"补充验证任务","sourceRefs":["GOAL:g1"],"impactNodeIds":[]}]}
                """;
        when(gateway.execute(anyString(), any(Request.class)))
                .thenReturn(new Response("r1", "c1", "test-model", invalid, Usage.of(10, 5), mapper.createObjectNode()))
                .thenReturn(new Response("r2", "c1", "test-model", repaired, Usage.of(20, 10), mapper.createObjectNode()));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");

        var result = service.generateCanvasProposal("account-1", "request-1", mapper.createObjectNode());

        assertThat(result.value().items()).hasSize(1);
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(gateway, times(2)).execute(anyString(), request.capture());
        Request repairRequest = request.getAllValues().get(1);
        assertThat(repairRequest.messages().get(3).content())
                .contains("ADD 必须包含 parentNodeId")
                .contains("现有 logicalNodeId 或 PROPOSAL:<key>");
    }

    @Test
    void nodeInferenceAcceptsAReviewablePrerequisiteRelation() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        String response = """
                {"items":[
                  {"key":"add_foundation","operation":"ADD","parentNodeId":"domain-1",
                   "nodeType":"KNOWLEDGE","title":"并发编程基础","status":"NOT_STARTED",
                   "detail":{"summary":"理解线程安全和同步原语"},"reason":"补齐前置知识",
                   "sourceRefs":["GOAL:g1"],"impactNodeIds":["skill-1"]},
                  {"key":"link_foundation","operation":"ADD_RELATION","relationType":"PREREQUISITE",
                   "fromNodeId":"PROPOSAL:add_foundation","toNodeId":"skill-1",
                   "reason":"并发基础是当前技能的前置能力","sourceRefs":["GOAL:g1"],
                   "impactNodeIds":["skill-1"]}
                ]}
                """;
        when(gateway.execute(anyString(), any(Request.class)))
                .thenReturn(new Response("r1", "c1", "test-model", response,
                        Usage.of(20, 10), mapper.createObjectNode()));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");

        var result = service.generateNodeInference("account-1", "request-1",
                mapper.createObjectNode(), CareerPlanningAiService.ProgressListener.NOOP);

        assertThat(result.value().items()).hasSize(2);
        var relation = result.value().items().get(1);
        assertThat(relation.operation()).isEqualTo("ADD_RELATION");
        assertThat(relation.relationType()).isEqualTo("PREREQUISITE");
        assertThat(relation.fromNodeId()).isEqualTo("PROPOSAL:add_foundation");
        assertThat(relation.toNodeId()).isEqualTo("skill-1");
    }

    @Test
    void nodeInferenceSemanticFailureIsRepairedBeforeQuotaSettlement() {
        AiGatewayService gateway = mock(AiGatewayService.class);
        AiQuotaService quota = mock(AiQuotaService.class);
        when(quota.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation("reservation-1", "request-1", 1, 0, "HELD"));
        String invalid = inferenceAddResponse("重复能力");
        String repaired = inferenceAddResponse("并发编程基础");
        when(gateway.execute(anyString(), any(Request.class)))
                .thenReturn(new Response("r1", "c1", "test-model", invalid,
                        Usage.of(20, 10), mapper.createObjectNode()))
                .thenReturn(new Response("r2", "c1", "test-model", repaired,
                        Usage.of(20, 10), mapper.createObjectNode()));
        CareerPlanningAiService service = new CareerPlanningAiService(gateway, quota, mapper, "qwen-plus");

        var result = service.generateNodeInference("account-1", "request-1", mapper.createObjectNode(),
                CareerPlanningAiService.ProgressListener.NOOP, payload -> {
                    if (payload.items().stream().anyMatch(item -> "重复能力".equals(item.title()))) {
                        throw AppException.dependency("CP_AI_INFERENCE_DUPLICATE", "节点推演包含现有或重复能力节点");
                    }
                });

        assertThat(result.value().items()).extracting(CareerPlanningAiService.AiProposalItem::title)
                .containsExactly("并发编程基础");
        ArgumentCaptor<Request> requests = ArgumentCaptor.forClass(Request.class);
        verify(gateway, times(2)).execute(anyString(), requests.capture());
        assertThat(requests.getAllValues().get(1).messages().get(3).content())
                .contains("节点推演包含现有或重复能力节点");
        assertThat(result.reservationId()).isEqualTo("reservation-1");
        verify(quota, never()).settle(anyString(), anyString(), anyInt());
        service.settle("account-1", result);
        verify(quota).settle("account-1", "reservation-1", 1);
    }

    private ObjectNode recommendationContext() {
        ObjectNode context = mapper.createObjectNode();
        context.putArray("allowedSourceRefs").add("PROFILE:p1").add("PROFILE_ITEM:p1");
        var candidates = context.putArray("taxonomyCandidates");
        candidates.addObject().put("id", "j1").put("title", "后端开发");
        candidates.addObject().put("id", "j2").put("title", "Java 开发");
        candidates.addObject().put("id", "j3").put("title", "测试开发");
        return context;
    }

    private static String recommendationResponse(String sourceRef) {
        return """
                {"status":"READY","recommendations":[
                  {"taxonomyNodeId":"j1","title":"后端开发","tier":"READY_NOW","fitSummary":"匹配","rationale":["课程项目"],"gaps":[],"sourceRefs":["%1$s"]},
                  {"taxonomyNodeId":"j2","title":"Java 开发","tier":"AFTER_SMALL_GAP","fitSummary":"相邻方向","rationale":["Java 实践"],"gaps":["工程经验"],"sourceRefs":["%1$s"]},
                  {"taxonomyNodeId":"j3","title":"测试开发","tier":"EXPLORATORY","fitSummary":"可探索","rationale":["自动化测试"],"gaps":["测试平台经验"],"sourceRefs":["%1$s"]}
                ],"insufficientReasons":[]}
                """.formatted(sourceRef);
    }

    private static String canvasResponse(String sourceRef) {
        return """
                {"nodes":[
                  {"key":"d1","type":"DOMAIN","title":"领域1","parentKey":"ROOT","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"d2","type":"DOMAIN","title":"领域2","parentKey":"ROOT","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"d3","type":"DOMAIN","title":"领域3","parentKey":"ROOT","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"s1","type":"SKILL","title":"领域1技能","parentKey":"d1","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"k1","type":"KNOWLEDGE","title":"领域1知识","parentKey":"d1","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"s2","type":"SKILL","title":"领域2技能","parentKey":"d2","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"k2","type":"KNOWLEDGE","title":"领域2知识","parentKey":"d2","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"s3","type":"SKILL","title":"领域3技能","parentKey":"d3","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"k3","type":"KNOWLEDGE","title":"领域3知识","parentKey":"d3","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"t1","type":"TASK","title":"领域1任务","parentKey":"s1","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"e1","type":"EVIDENCE","title":"领域1证据","parentKey":"t1","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"t2","type":"TASK","title":"领域2任务","parentKey":"s2","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"e2","type":"EVIDENCE","title":"领域2证据","parentKey":"t2","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"t3","type":"TASK","title":"领域3任务","parentKey":"s3","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]},
                  {"key":"e3","type":"EVIDENCE","title":"领域3证据","parentKey":"t3","detail":{},"sourceRefs":["%1$s"],"prerequisiteKeys":[]}
                ]}
                """.formatted(sourceRef);
    }

    private static String inferenceAddResponse(String title) {
        return """
                {"items":[{"key":"add_foundation","operation":"ADD","parentNodeId":"domain-1",
                  "nodeType":"KNOWLEDGE","title":"%s","status":"NOT_STARTED",
                  "detail":{"summary":"理解线程安全和同步原语"},"reason":"补齐前置知识",
                  "sourceRefs":["GOAL:g1"],"impactNodeIds":["skill-1"]}]}
                """.formatted(title);
    }
}
