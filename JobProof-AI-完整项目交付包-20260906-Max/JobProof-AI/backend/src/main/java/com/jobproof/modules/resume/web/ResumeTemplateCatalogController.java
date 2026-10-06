package com.jobproof.modules.resume.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.resume.application.ResumeTemplateCatalogService;
import com.jobproof.modules.resume.application.ResumeTemplateCatalogService.CatalogItemView;
import com.jobproof.modules.resume.application.ResumeTemplateCatalogService.CatalogQuery;
import com.jobproof.modules.resume.application.ResumeTemplateCatalogService.DownloadView;
import com.jobproof.modules.resume.application.ResumeTemplateCatalogService.FacetGroupView;
import com.jobproof.modules.resume.application.ResumeTemplateCatalogService.PreviewImageView;
import com.jobproof.shared.page.PageResult;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/template-catalog")
public class ResumeTemplateCatalogController {
    private static final MediaType DOCX=MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final ResumeTemplateCatalogService service;
    public ResumeTemplateCatalogController(ResumeTemplateCatalogService service){this.service=service;}

    @GetMapping
    public ApiResponse<PageResult<CatalogItemView>> catalog(@Valid @ModelAttribute CatalogQuery query){return ApiResponse.ok(service.catalog(query));}
    @GetMapping("/facets")
    public ApiResponse<List<FacetGroupView>> facets(@RequestParam(required=false) String assetKind){return ApiResponse.ok(service.availableFacets(assetKind));}
    @GetMapping("/{id}")
    public ApiResponse<CatalogItemView> detail(@PathVariable String id){return ApiResponse.ok(service.detail(id));}
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id){
        DownloadView download=service.download(SecurityConfig.currentAccount(),id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff").header("X-Template-SHA256",download.fileHash())
                .header("X-Template-Attribution","HICV.cn").contentType(DOCX).contentLength(download.body().length).body(download.body());
    }
    @GetMapping(value="/{id}/thumbnail",produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> thumbnail(@PathVariable String id){
        PreviewImageView thumbnail=service.thumbnail(id);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"public, max-age=31536000, immutable")
                .header(HttpHeaders.ETAG,"\""+thumbnail.etag()+"\"")
                .header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.IMAGE_PNG).contentLength(thumbnail.body().length).body(thumbnail.body());
    }

    @GetMapping(value="/{id}/preview-pages/{pageNumber}",produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> previewPage(@PathVariable String id,@PathVariable int pageNumber){
        PreviewImageView preview=service.previewPage(id,pageNumber);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"public, max-age=31536000, immutable")
                .header(HttpHeaders.ETAG,"\""+preview.etag()+"\"")
                .header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.IMAGE_PNG).contentLength(preview.body().length).body(preview.body());
    }
}
