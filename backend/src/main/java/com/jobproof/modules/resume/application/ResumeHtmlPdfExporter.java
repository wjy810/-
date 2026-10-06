package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumeAtsTextCheck;
import com.jobproof.modules.resume.domain.ResumeGenericLayout;
import com.jobproof.modules.task.application.TaskService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Step 2 of an HTML-template PDF export: call the renderer service with no transaction open, enforce
 * the page limit (EXP-04), run the ATS text check (EXP-03), then hand the result to step 3.
 * A renderer outage fails the task as retryable; nothing is ever rendered by another engine (EXP-05).
 */
@Service
public class ResumeHtmlPdfExporter {
    private static final Logger log = LoggerFactory.getLogger(ResumeHtmlPdfExporter.class);

    private final ResumeRenderPort renderer;
    private final ResumeService resumeService;
    private final TaskService taskService;
    private final ObjectMapper mapper;

    public ResumeHtmlPdfExporter(ResumeRenderPort renderer, ResumeService resumeService, TaskService taskService,
            ObjectMapper mapper) {
        this.renderer = renderer;
        this.resumeService = resumeService;
        this.taskService = taskService;
        this.mapper = mapper;
    }

    public void export(ResumeService.HtmlPdfJob job) {
        ResumeLayoutCoordinator.HtmlTemplate html = job.context().html();
        taskService.updateProgress(job.taskId(), 40, "RENDERING");
        ResumeRenderPort.RenderedDocument pdf;
        try {
            pdf = renderer.render(ResumeRenderPort.RenderRequest.pdf(
                    job.context().templateId(),
                    html.design().toJson(mapper),
                    html.content(),
                    html.photoDataUrl(),
                    job.title(),
                    html.pageLimit()));
        } catch (ResumeRenderPort.RenderFailedException exception) {
            log.warn("resume pdf render failed taskId={} code={}", job.taskId(), exception.code());
            String reason = exception.retryable()
                    ? "排版服务暂时不可用，未生成文件，请稍后重试"
                    : "排版服务无法处理该简历，请联系我们";
            resumeService.failHtmlPdfExport(job, "RENDERER_UNAVAILABLE", reason,
                    Map.of("rendererCode", exception.code(), "retryable", exception.retryable()));
            return;
        }
        if (pdf.pageCount() > html.pageLimit()) {
            String section = pdf.overflowSection() == null ? null
                    : ResumeGenericLayout.title(html.manifest(), html.design(), pdf.overflowSection());
            String reason = "内容排版后共 " + pdf.pageCount() + " 页，超出 " + html.pageLimit() + " 页上限"
                    + (pdf.overflowMm() > 0 ? "约 " + Math.round(pdf.overflowMm()) + " 毫米" : "")
                    + (section == null ? "" : "，从「" + section + "」开始")
                    + "。请精简内容、使用一键紧凑或调整目标页数后重新导出";
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("pageCount", pdf.pageCount());
            details.put("pageLimit", html.pageLimit());
            details.put("overflowMm", pdf.overflowMm());
            details.put("overflowSection", pdf.overflowSection());
            resumeService.failHtmlPdfExport(job, "RESUME_PAGE_LIMIT_EXCEEDED", reason, details);
            return;
        }
        taskService.updateProgress(job.taskId(), 80, "CHECKING_TEXT");
        ResumeAtsTextCheck.Result ats = ResumeAtsTextCheck.check(pdf.body(),
                ResumeAtsTextCheck.expectation(html.manifest(), html.design(), html.content()));
        if (!ats.passed()) {
            log.warn("resume pdf ats check failed taskId={} template={} checks={}", job.taskId(),
                    job.context().templateId(), ats.checks());
        }
        resumeService.completeHtmlPdfExport(job, pdf, ats);
    }
}
