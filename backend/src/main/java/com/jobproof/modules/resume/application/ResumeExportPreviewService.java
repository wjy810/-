package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumeGenericLayout;
import com.jobproof.modules.resume.domain.ResumePdfExportMode;
import com.jobproof.shared.error.AppException;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * First page of the PDF an export would produce, rendered by the renderer service (EXP-02). The
 * preview in the browser uses the same templates, but only the renderer's Chromium is authoritative
 * for line breaks; the user sees this before exporting. Reads in a short transaction, renders outside.
 */
@Service
public class ResumeExportPreviewService {
    private final ResumeService resumes;
    private final ResumeRenderPort renderer;
    private final ObjectMapper mapper;

    public ResumeExportPreviewService(ResumeService resumes, ResumeRenderPort renderer, ObjectMapper mapper) {
        this.resumes = resumes;
        this.renderer = renderer;
        this.mapper = mapper;
    }

    public PreviewView preview(String accountId, String masterId, ResumePdfExportMode mode) {
        ResumeService.HtmlPdfJob job = resumes.exportPreview(accountId, masterId, mode)
                .orElseThrow(() -> AppException.conflict("RESUME_EXPORT_PREVIEW_UNSUPPORTED", "当前版式不支持导出预览"));
        ResumeLayoutCoordinator.HtmlTemplate html = job.context().html();
        ResumeRenderPort.RenderedDocument image;
        try {
            image = renderer.render(new ResumeRenderPort.RenderRequest("png", job.context().templateId(),
                    html.design().toJson(mapper), html.content(), html.photoDataUrl(), job.title(),
                    html.pageLimit(), true));
        } catch (ResumeRenderPort.RenderFailedException exception) {
            throw AppException.dependency("RENDERER_UNAVAILABLE", "排版服务暂时不可用，无法生成导出预览");
        }
        String section = image.overflowSection() == null ? null
                : ResumeGenericLayout.title(html.manifest(), html.design(), image.overflowSection());
        return new PreviewView(image.pageCount(), html.pageLimit(), image.overflowMm(), section,
                "data:image/png;base64," + Base64.getEncoder().encodeToString(image.body()));
    }

    public record PreviewView(int pageCount, int pageLimit, double overflowMm, String overflowSection,
            String firstPageImage) {}
}
