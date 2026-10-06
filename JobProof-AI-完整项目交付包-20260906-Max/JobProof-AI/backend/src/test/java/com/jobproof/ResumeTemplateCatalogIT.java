package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.audit.infra.AuditEventJpaRepository;
import com.jobproof.modules.resume.application.ResumeTemplateBatchImportService;
import com.jobproof.modules.resume.domain.ResumeTemplateDocxInspector;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogFacetEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogFacetJpaRepository;
import com.jobproof.modules.storage.ObjectStoragePort;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "jobproof.dev.admin.enabled=true",
        "jobproof.dev.admin.alias=admin",
        "jobproof.dev.admin.email=admin@jobproof.local",
        "jobproof.dev.admin.password=admin"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ResumeTemplateCatalogIT {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired ResumeTemplateAssetJpaRepository assets;
    @Autowired ResumeTemplateCatalogEntryJpaRepository entries;
    @Autowired ResumeTemplateCatalogFacetJpaRepository facets;
    @Autowired ObjectStoragePort storage;
    @Autowired AuditEventJpaRepository audit;

    @Test
    void anonymousCatalogAndDetailArePublicButDownloadRequiresLoginAndAuditsOriginalBytes() throws Exception {
        Fixture fixture=fixture("前端开发应届生简历",docx("前端开发经历"));

        mockMvc.perform(get("/api/v1/template-catalog").queryParam("keyword","前端"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(fixture.catalogId()))
                .andExpect(jsonPath("$.data.items[0].thumbnailUri")
                        .value("/api/v1/template-catalog/"+fixture.catalogId()+"/thumbnail?v=test-renderer-v1"))
                .andExpect(jsonPath("$.data.items[0].facets[0].code").value("TECHNOLOGY"));
        mockMvc.perform(get("/api/v1/template-catalog/{id}",fixture.catalogId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("前端开发应届生简历"))
                .andExpect(jsonPath("$.data.previewStatus").value("READY"))
                .andExpect(jsonPath("$.data.previewPageCount").value(2))
                .andExpect(jsonPath("$.data.previewPages.length()").value(2))
                .andExpect(jsonPath("$.data.previewPages[1].imageUri")
                        .value("/api/v1/template-catalog/"+fixture.catalogId()+"/preview-pages/2?v=test-renderer-v1"));
        mockMvc.perform(get("/api/v1/template-catalog/{id}/thumbnail",fixture.catalogId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"));
        mockMvc.perform(get("/api/v1/template-catalog/{id}/preview-pages/2",fixture.catalogId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
        mockMvc.perform(get("/api/v1/template-catalog/{id}/preview-pages/3",fixture.catalogId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("TEMPLATE_PREVIEW_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/template-catalog/{id}/download",fixture.catalogId()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        Cookie seeker=registerAndLogin("catalog-seeker+"+System.nanoTime()+"@example.com");
        MvcResult downloaded=mockMvc.perform(get("/api/v1/template-catalog/{id}/download",fixture.catalogId()).cookie(seeker))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .andExpect(header().string("X-Template-SHA256",fixture.hash()))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("HICV-")))
                .andReturn();
        assertThat(downloaded.getResponse().getContentAsByteArray()).isEqualTo(fixture.bytes());
        assertThat(audit.findAll()).anyMatch(event->"TEMPLATE_DOCX_DOWNLOADED".equals(event.getAction())
                &&fixture.catalogId().equals(event.getObjectId()));
    }

    @Test
    void administratorCanDownloadOriginalTemplate() throws Exception {
        Fixture fixture=fixture("管理员下载模板",docx("管理员可下载"));
        Cookie admin=login("admin","admin");

        MvcResult downloaded=mockMvc.perform(get("/api/v1/template-catalog/{id}/download",fixture.catalogId()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .andExpect(header().string("X-Template-SHA256",fixture.hash()))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("HICV-")))
                .andReturn();
        assertThat(downloaded.getResponse().getContentAsByteArray()).isEqualTo(fixture.bytes());
    }

    @Test
    void retiredAndStorageTamperedAssetsCannotBeDownloaded() throws Exception {
        Cookie seeker=registerAndLogin("catalog-gates+"+System.nanoTime()+"@example.com");
        Fixture retired=fixture("退休模板",docx("退休"));
        ResumeTemplateCatalogEntryEntity retiredEntry=entries.findById(retired.catalogId()).orElseThrow();
        retiredEntry.setPublicationStatus("RETIRED");entries.saveAndFlush(retiredEntry);
        mockMvc.perform(get("/api/v1/template-catalog/{id}/download",retired.catalogId()).cookie(seeker))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.reason").value("TEMPLATE_CATALOG_NOT_FOUND"));

        Fixture tampered=fixture("被篡改模板",docx("原始内容"));
        ResumeTemplateAssetEntity tamperedAsset=assets.findById(tampered.assetId()).orElseThrow();
        storage.put(tamperedAsset.getStorageKey(),docx("篡改后内容"));
        mockMvc.perform(get("/api/v1/template-catalog/{id}/download",tampered.catalogId()).cookie(seeker))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.reason").value("TEMPLATE_STORAGE_INTEGRITY_FAILED"));
    }

    private Fixture fixture(String title,byte[] bytes){
        Instant now=Instant.now();String assetId=UUID.randomUUID().toString();String catalogId=UUID.randomUUID().toString();
        String hash=ResumeTemplateDocxInspector.sha256(bytes);String key="test-template-assets/"+assetId+"/"+hash+".docx";
        storage.put(key,bytes);
        ResumeTemplateAssetEntity asset=new ResumeTemplateAssetEntity();asset.setId(assetId);asset.setSourceName(title);asset.setOriginalFilename(title+".docx");
        asset.setSourceRelativePath("09_行业专属/"+title+".docx");asset.setSourceUri("https://github.com/HICV-CN/hicv-word-resume-templates");
        asset.setLicenseStatus("APPROVED");asset.setFileHash(hash);asset.setStorageKey(key);asset.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        asset.setSizeBytes((long)bytes.length);asset.setScanStatus("PASSED");asset.setStatus("APPROVED");asset.setVersionNo(0);asset.setCreatedAt(now);asset.setUpdatedAt(now);assets.save(asset);
        ResumeTemplateCatalogEntryEntity entry=new ResumeTemplateCatalogEntryEntity();entry.setId(catalogId);entry.setEntryType("DOCX_ASSET");entry.setReferenceId(assetId);entry.setTitle(title);
        entry.setSummary("测试目录资产");entry.setCapability("DOCX_DOWNLOAD");entry.setAssetKind("RESUME");entry.setLanguageCode("zh-CN");entry.setPageCount("2");entry.setPhotoPolicy("UNSPECIFIED");
        entry.setSourceName("HICV.cn");entry.setSourceUri(asset.getSourceUri());entry.setAttribution("来源：HICV.cn");entry.setSearchText(title+" 前端 技术研发 应届生");entry.setPublicationStatus("PUBLISHED");
        entry.setPreviewStatus("READY");entry.setPreviewPageCount(2);entry.setPreviewRendererVersion("test-renderer-v1");entry.setPreviewUpdatedAt(now);
        entry.setDownloadCount(0);entry.setPublishedAt(now);entry.setCreatedAt(now);entry.setUpdatedAt(now);entry.setVersionNo(0);entries.save(entry);
        entry.setThumbnailUri("/api/v1/template-catalog/"+catalogId+"/thumbnail");entries.save(entry);
        storage.put(com.jobproof.modules.resume.application.ResumeTemplatePreviewService.previewKey(catalogId,hash,1),png());
        storage.put(com.jobproof.modules.resume.application.ResumeTemplatePreviewService.previewKey(catalogId,hash,2),png());
        ResumeTemplateCatalogFacetEntity facet=new ResumeTemplateCatalogFacetEntity();facet.setId(UUID.randomUUID().toString());facet.setCatalogEntryId(catalogId);facet.setFacetType("OCCUPATION");facet.setFacetCode("TECHNOLOGY");facet.setFacetLabel("技术研发");facets.save(facet);
        return new Fixture(assetId,catalogId,hash,bytes);
    }

    private static byte[] docx(String text){
        try{ByteArrayOutputStream output=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(output)){
            write(zip,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            write(zip,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            write(zip,"word/document.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>"+text+"</w:t></w:r></w:p><w:sectPr/></w:body></w:document>");
        }return output.toByteArray();}catch(Exception exception){throw new IllegalStateException(exception);}
    }
    private static void write(ZipOutputStream zip,String name,String value)throws Exception{zip.putNextEntry(new ZipEntry(name));zip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
    private static byte[] png(){return java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");}
    private Cookie registerAndLogin(String email)throws Exception{mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email",email,"password","Passw0rd!")))).andExpect(status().isOk());return login(email,"Passw0rd!");}
    private Cookie login(String email,String password)throws Exception{MvcResult login=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email",email,"password",password)))).andExpect(status().isOk()).andReturn();return login.getResponse().getCookie("jobproof_session");}
    private record Fixture(String assetId,String catalogId,String hash,byte[] bytes){}
}
