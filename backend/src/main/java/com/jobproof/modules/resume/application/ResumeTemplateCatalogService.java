package com.jobproof.modules.resume.application;

import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogFacetEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogFacetJpaRepository;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import com.jobproof.shared.concurrency.Versions;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriUtils;

@Service
public class ResumeTemplateCatalogService {
    private final ResumeTemplateCatalogEntryJpaRepository entries;
    private final ResumeTemplateCatalogFacetJpaRepository facets;
    private final ResumeTemplateAssetJpaRepository assets;
    private final ObjectStoragePort storage;
    private final AuditService audit;
    private final ClockPort clock;
    private final boolean publicEnabled;
    private final boolean commercialMode;
    private final Object snapshotLock = new Object();
    private volatile CatalogSnapshot cachedSnapshot;

    public ResumeTemplateCatalogService(ResumeTemplateCatalogEntryJpaRepository entries,
            ResumeTemplateCatalogFacetJpaRepository facets, ResumeTemplateAssetJpaRepository assets,
            ObjectStoragePort storage, AuditService audit, ClockPort clock,
            @Value("${jobproof.templates.catalog-public-enabled:true}") boolean publicEnabled,
            @Value("${jobproof.templates.commercial-mode:false}") boolean commercialMode) {
        this.entries=entries; this.facets=facets; this.assets=assets; this.storage=storage; this.audit=audit; this.clock=clock;
        this.publicEnabled=publicEnabled; this.commercialMode=commercialMode;
    }

    @Transactional(readOnly=true)
    public PageResult<CatalogItemView> catalog(CatalogQuery query) {
        CatalogSnapshot snapshot = publicSnapshot();
        List<ScoredEntry> matched = new ArrayList<>();
        for (ResumeTemplateCatalogEntryEntity entry : snapshot.entries()) {
            List<FacetView> entryFacets = snapshot.facetsByEntry().getOrDefault(entry.getId(), List.of());
            int score = score(entry, entryFacets, query);
            if (score >= 0) matched.add(new ScoredEntry(entry, entryFacets, score));
        }
        matched.sort(Comparator.comparingInt(ScoredEntry::score).reversed()
                .thenComparing((ScoredEntry value) -> value.entry().getDownloadCount(), Comparator.reverseOrder())
                .thenComparing(value -> value.entry().getPublishedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(value -> value.entry().getTitle()));
        int from=Math.min(query.page()*query.size(), matched.size());
        int to=Math.min(from+query.size(), matched.size());
        List<CatalogItemView> page=matched.subList(from,to).stream().map(value->view(value.entry(),value.facets(),false)).toList();
        return new PageResult<>(page, matched.size(), query.page(), query.size());
    }

    @Transactional(readOnly=true)
    public CatalogItemView detail(String id) {
        ResumeTemplateCatalogEntryEntity entry=requirePublished(id);
        return view(entry, facets.findByCatalogEntryId(id).stream().map(this::facetView).toList(),true);
    }

    @Transactional(readOnly=true)
    public List<FacetGroupView> availableFacets(String assetKind) {
        CatalogSnapshot snapshot=publicSnapshot();
        List<ResumeTemplateCatalogEntryEntity> visible=snapshot.entries().stream()
                .filter(entry->assetKind==null||assetKind.isBlank()||entry.getAssetKind().equalsIgnoreCase(assetKind)).toList();
        Set<String> visibleIds=visible.stream().map(ResumeTemplateCatalogEntryEntity::getId).collect(Collectors.toSet());
        Map<String,Map<String,FacetCounter>> grouped=new LinkedHashMap<>();
        snapshot.facetsByEntry().entrySet().stream().filter(entry->visibleIds.contains(entry.getKey()))
                .flatMap(entry->entry.getValue().stream()).forEach(facet->grouped
                .computeIfAbsent(facet.type(),ignored->new LinkedHashMap<>())
                .compute(facet.code(),(code,current)->current==null
                        ? new FacetCounter(facet.code(),facet.label(),1)
                        : new FacetCounter(current.code(),current.label(),current.count()+1)));
        return grouped.entrySet().stream().map(group->new FacetGroupView(group.getKey(),group.getValue().values().stream()
                .map(value->new FacetOptionView(value.code(),value.label(),value.count())).toList())).toList();
    }

    @Transactional
    public DownloadView download(CurrentAccount current,String id) {
        if (current==null||!Set.of("SEEKER","ADMIN").contains(current.role()))
            throw AppException.forbidden("TEMPLATE_DOWNLOAD_FORBIDDEN","仅登录用户可以下载模板");
        ResumeTemplateCatalogEntryEntity entry=requirePublished(id);
        if (!"DOCX_ASSET".equals(entry.getEntryType())||!"DOCX_DOWNLOAD".equals(entry.getCapability()))
            throw AppException.conflict("TEMPLATE_DOWNLOAD_UNAVAILABLE","该目录项不提供原始 DOCX 下载");
        ResumeTemplateAssetEntity asset=assets.findById(entry.getReferenceId())
                .orElseThrow(()->AppException.conflict("TEMPLATE_ASSET_MISSING","模板资产不存在"));
        if (!"APPROVED".equals(asset.getStatus())||!"PASSED".equals(asset.getScanStatus())||asset.getStorageKey()==null)
            throw AppException.conflict("TEMPLATE_DOWNLOAD_GATE_FAILED","模板未通过安全和授权门禁");
        byte[] body=storage.get(asset.getStorageKey());
        String actual=com.jobproof.modules.resume.domain.ResumeTemplateDocxInspector.sha256(body);
        if (!actual.equals(asset.getFileHash())) throw AppException.conflict("TEMPLATE_STORAGE_INTEGRITY_FAILED","模板存储完整性校验失败");
        entry.setDownloadCount(entry.getDownloadCount()+1);
        entry.setUpdatedAt(clock.now()); entries.save(entry);
        invalidatePublicSnapshot();
        audit.append(current.accountId(),"TEMPLATE_DOCX_DOWNLOADED","RESUME_TEMPLATE_CATALOG",entry.getId(),
                "hash="+asset.getFileHash().substring(0,12)+" source=HICV.cn");
        return new DownloadView(downloadFilename(asset),asset.getFileHash(),body);
    }

    @Transactional(readOnly=true)
    public PreviewImageView thumbnail(String id) {
        return previewPage(id, 1);
    }

    @Transactional(readOnly=true)
    public PreviewImageView previewPage(String id, int pageNumber) {
        ResumeTemplateCatalogEntryEntity entry=requirePublished(id);
        if (!"DOCX_ASSET".equals(entry.getEntryType()))
            throw AppException.user("TEMPLATE_PREVIEW_NOT_FOUND","该目录项没有服务端分页预览");
        if (!"READY".equals(entry.getPreviewStatus()) || pageNumber < 1 || pageNumber > entry.getPreviewPageCount())
            throw AppException.user("TEMPLATE_PREVIEW_NOT_FOUND","模板分页预览尚未生成或页码不存在");
        ResumeTemplateAssetEntity asset=assets.findById(entry.getReferenceId())
                .orElseThrow(()->AppException.user("TEMPLATE_PREVIEW_NOT_FOUND","模板分页预览不存在"));
        byte[] body;
        try { body=storage.get(ResumeTemplatePreviewService.previewKey(entry.getId(),asset.getFileHash(),pageNumber)); }
        catch (RuntimeException exception) { throw AppException.user("TEMPLATE_PREVIEW_NOT_FOUND","模板分页预览不存在"); }
        if (body.length<8||body[0]!=(byte)0x89||body[1]!=0x50||body[2]!=0x4e||body[3]!=0x47)
            throw AppException.conflict("TEMPLATE_PREVIEW_INTEGRITY_FAILED","模板分页预览存储校验失败");
        return new PreviewImageView(asset.getFileHash()+"-"+pageNumber+"-"+entry.getPreviewRendererVersion(),body);
    }

    private ResumeTemplateCatalogEntryEntity requirePublished(String id) {
        ResumeTemplateCatalogEntryEntity entry=entries.findById(id)
                .orElseThrow(()->AppException.user("TEMPLATE_CATALOG_NOT_FOUND","模板目录项不存在"));
        if (!visible(entry))
            throw AppException.user("TEMPLATE_CATALOG_NOT_FOUND","模板目录项不存在");
        return entry;
    }

    @Transactional(readOnly=true)
    public PageResult<CatalogAdminItemView> adminCatalog(CurrentAccount current, PageQuery query) {
        assertAdmin(current);
        var page=entries.findAllByOrderByUpdatedAtDesc(PageRequest.of(query.page(),query.size()));
        Map<String,List<FacetView>> facetMap=facetMap(page.getContent());
        return new PageResult<>(page.getContent().stream().map(entry->adminView(entry,
                facetMap.getOrDefault(entry.getId(),List.of()))).toList(),page.getTotalElements(),query.page(),query.size());
    }

    @Transactional
    public CatalogAdminItemView updateMetadata(CurrentAccount current,String id,CatalogMetadataCommand command,
            Integer expectedVersion) {
        assertAdmin(current);
        ResumeTemplateCatalogEntryEntity entry=requireEntry(id);
        Versions.assertExpected(expectedVersion,entry.getVersionNo());
        String title=required(command.title(),512,"TEMPLATE_CATALOG_TITLE_INVALID","目录标题必填且不能超过 512 字符");
        String summary=optional(command.summary(),1024,"TEMPLATE_CATALOG_SUMMARY_INVALID","目录摘要不能超过 1024 字符");
        entry.setTitle(title);entry.setSummary(summary);
        entry.setAssetKind(oneOf(command.assetKind(),Set.of("RESUME","COVER","SCHOOL_APPLICATION","COVER_LETTER"),"TEMPLATE_CATALOG_ASSET_KIND_INVALID"));
        entry.setLanguageCode(oneOf(command.languageCode(),Set.of("zh-CN","en","mixed"),"TEMPLATE_CATALOG_LANGUAGE_INVALID"));
        entry.setPageCount(optional(command.pageCount(),16,"TEMPLATE_CATALOG_PAGE_COUNT_INVALID","页数标识不能超过 16 字符"));
        entry.setPhotoPolicy(oneOf(command.photoPolicy(),Set.of("DISABLED","OPTIONAL","REQUIRED","UNSPECIFIED"),"TEMPLATE_CATALOG_PHOTO_POLICY_INVALID"));
        entry.setThumbnailUri(optional(command.thumbnailUri(),1024,"TEMPLATE_CATALOG_THUMBNAIL_INVALID","缩略图地址不能超过 1024 字符"));
        entry.setVersionNo(entry.getVersionNo()+1);entry.setUpdatedAt(clock.now());entries.save(entry);
        facets.deleteByCatalogEntryId(entry.getId());
        facets.flush();
        List<CatalogFacetCommand> requested=command.facets()==null?List.of():command.facets();
        for(CatalogFacetCommand value:requested) saveFacet(entry.getId(),value);
        entry.setSearchText(searchText(entry,requested));entries.save(entry);
        invalidatePublicSnapshot();
        audit.append(current.accountId(),"TEMPLATE_CATALOG_METADATA_UPDATED","RESUME_TEMPLATE_CATALOG",entry.getId(),"version="+entry.getVersionNo());
        return adminView(entry,facets.findByCatalogEntryId(entry.getId()).stream().map(this::facetView).toList());
    }

    @Transactional
    public CatalogAdminItemView publish(CurrentAccount current,String id,Integer expectedVersion) {
        assertAdmin(current);ResumeTemplateCatalogEntryEntity entry=requireEntry(id);Versions.assertExpected(expectedVersion,entry.getVersionNo());
        assertPublicationGate(entry);
        Instant now=clock.now();entry.setPublicationStatus("PUBLISHED");entry.setPublishedAt(entry.getPublishedAt()==null?now:entry.getPublishedAt());
        entry.setRetiredAt(null);entry.setVersionNo(entry.getVersionNo()+1);entry.setUpdatedAt(now);entries.save(entry);
        invalidatePublicSnapshot();
        audit.append(current.accountId(),"TEMPLATE_CATALOG_PUBLISHED","RESUME_TEMPLATE_CATALOG",id,"version="+entry.getVersionNo());
        return adminView(entry,facets.findByCatalogEntryId(id).stream().map(this::facetView).toList());
    }

    @Transactional
    public CatalogAdminItemView retire(CurrentAccount current,String id,Integer expectedVersion) {
        assertAdmin(current);ResumeTemplateCatalogEntryEntity entry=requireEntry(id);Versions.assertExpected(expectedVersion,entry.getVersionNo());
        Instant now=clock.now();entry.setPublicationStatus("RETIRED");entry.setRetiredAt(now);entry.setVersionNo(entry.getVersionNo()+1);entry.setUpdatedAt(now);entries.save(entry);
        invalidatePublicSnapshot();
        audit.append(current.accountId(),"TEMPLATE_CATALOG_RETIRED","RESUME_TEMPLATE_CATALOG",id,"version="+entry.getVersionNo());
        return adminView(entry,facets.findByCatalogEntryId(id).stream().map(this::facetView).toList());
    }

    private List<ResumeTemplateCatalogEntryEntity> visibleEntries(){
        return publicEnabled?entries.findByPublicationStatus("PUBLISHED").stream().filter(this::visible).toList():List.of();
    }

    public void invalidatePublicSnapshot() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cachedSnapshot=null; }
            });
        } else {
            cachedSnapshot=null;
        }
    }

    private CatalogSnapshot publicSnapshot() {
        CatalogSnapshot current=cachedSnapshot;
        if(current!=null)return current;
        synchronized(snapshotLock){
            current=cachedSnapshot;
            if(current!=null)return current;
            List<ResumeTemplateCatalogEntryEntity> visible=List.copyOf(visibleEntries());
            Map<String,List<FacetView>> loaded=facetMap(visible);
            Map<String,List<FacetView>> immutable=loaded.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,entry->List.copyOf(entry.getValue())));
            current=new CatalogSnapshot(visible,immutable);
            cachedSnapshot=current;
            return current;
        }
    }
    private boolean visible(ResumeTemplateCatalogEntryEntity entry){
        return publicEnabled&&"PUBLISHED".equals(entry.getPublicationStatus())
                &&!(commercialMode&&"DOCX_ASSET".equals(entry.getEntryType())&&"HICV.cn".equals(entry.getSourceName()));
    }
    private ResumeTemplateCatalogEntryEntity requireEntry(String id){return entries.findById(id).orElseThrow(()->AppException.user("TEMPLATE_CATALOG_NOT_FOUND","模板目录项不存在"));}
    private void assertPublicationGate(ResumeTemplateCatalogEntryEntity entry){
        if("DOCX_ASSET".equals(entry.getEntryType())){
            ResumeTemplateAssetEntity asset=assets.findById(entry.getReferenceId()).orElseThrow(()->AppException.conflict("TEMPLATE_ASSET_MISSING","模板资产不存在"));
            if(!"APPROVED".equals(asset.getStatus())||!"PASSED".equals(asset.getScanStatus())||asset.getStorageKey()==null)
                throw AppException.conflict("TEMPLATE_CATALOG_PUBLISH_GATE_FAILED","原始资产未通过授权、安全或存储门禁");
        } else if(!"SMART_TEMPLATE".equals(entry.getEntryType())) throw AppException.conflict("TEMPLATE_CATALOG_ENTRY_TYPE_INVALID","未知目录项类型");
    }
    private void saveFacet(String entryId,CatalogFacetCommand value){
        String type=oneOf(value.type(),Set.of("OCCUPATION","JOB","STYLE","CAREER_STAGE"),"TEMPLATE_CATALOG_FACET_TYPE_INVALID");
        String code=required(value.code(),64,"TEMPLATE_CATALOG_FACET_CODE_INVALID","标签编码必填且不能超过 64 字符");
        String label=required(value.label(),128,"TEMPLATE_CATALOG_FACET_LABEL_INVALID","标签名称必填且不能超过 128 字符");
        ResumeTemplateCatalogFacetEntity facet=new ResumeTemplateCatalogFacetEntity();facet.setId(com.jobproof.shared.id.Ids.newId());facet.setCatalogEntryId(entryId);facet.setFacetType(type);facet.setFacetCode(code);facet.setFacetLabel(label);facets.save(facet);
    }
    private String searchText(ResumeTemplateCatalogEntryEntity entry,List<CatalogFacetCommand> values){
        String origin="";
        if("DOCX_ASSET".equals(entry.getEntryType())){ResumeTemplateAssetEntity asset=assets.findById(entry.getReferenceId()).orElse(null);if(asset!=null)origin=(asset.getOriginalFilename()==null?"":asset.getOriginalFilename())+" "+(asset.getSourceRelativePath()==null?"":asset.getSourceRelativePath());}
        return (entry.getTitle()+" "+origin+" "+values.stream().map(CatalogFacetCommand::label).filter(java.util.Objects::nonNull).collect(Collectors.joining(" "))).trim();
    }
    private CatalogAdminItemView adminView(ResumeTemplateCatalogEntryEntity entry,List<FacetView> values){return new CatalogAdminItemView(view(entry,values,true),entry.getPublicationStatus(),entry.getRetiredAt(),entry.getUpdatedAt(),entry.getVersionNo());}
    private static void assertAdmin(CurrentAccount current){if(current==null||!"ADMIN".equals(current.role()))throw AppException.forbidden("ADMIN_ONLY","仅管理员可以管理模板目录");}
    private static String required(String value,int max,String reason,String message){String result=value==null?"":value.trim();if(result.isEmpty()||result.length()>max)throw AppException.user(reason,message);return result;}
    private static String optional(String value,int max,String reason,String message){if(value==null||value.isBlank())return null;String result=value.trim();if(result.length()>max)throw AppException.user(reason,message);return result;}
    private static String oneOf(String value,Set<String> allowed,String reason){String result=value==null?"":value.trim();if(!allowed.contains(result))throw AppException.user(reason,"目录元数据取值无效");return result;}

    private String downloadFilename(ResumeTemplateAssetEntity asset) {
        String original=asset.getOriginalFilename()==null?asset.getSourceName():asset.getOriginalFilename();
        String safe=original.replaceAll("[\\r\\n\\\\/:*?\"<>|]","_");
        return safe.toLowerCase(Locale.ROOT).endsWith(".docx")?"HICV-"+safe:"HICV-"+safe+".docx";
    }

    private Map<String,List<FacetView>> facetMap(List<ResumeTemplateCatalogEntryEntity> visible) {
        if (visible.isEmpty()) return Map.of();
        return facets.findByCatalogEntryIdIn(visible.stream().map(ResumeTemplateCatalogEntryEntity::getId).toList())
                .stream().map(this::facetView).collect(Collectors.groupingBy(FacetView::catalogEntryId));
    }

    private int score(ResumeTemplateCatalogEntryEntity entry,List<FacetView> values,CatalogQuery query) {
        if (!matches(entry.getCapability(),query.capability)||!matches(entry.getAssetKind(),query.assetKind)
                ||!matches(entry.getLanguageCode(),query.language)||!matches(entry.getPageCount(),query.pages)
                ||!matches(entry.getPhotoPolicy(),query.photoPolicy)) return -1;
        if (!facetMatches(values,"OCCUPATION",query.occupation)||!facetMatches(values,"STYLE",query.style)
                ||!facetMatches(values,"JOB",query.jobTag)||!facetMatches(values,"CAREER_STAGE",query.careerStage)) return -1;
        String keyword=normalize(query.keyword);
        if (keyword.isBlank()) return 0;
        String title=normalize(entry.getTitle());
        String haystack=normalize(entry.getSearchText()+" "+values.stream().map(FacetView::label).collect(Collectors.joining(" ")));
        for (String token:keyword.split("\\s+")) if (!haystack.contains(token)) return -1;
        if (title.equals(keyword)) return 100;
        if (title.startsWith(keyword)) return 80;
        if (values.stream().anyMatch(value->normalize(value.label()).equals(keyword))) return 70;
        return title.contains(keyword)?50:20;
    }

    private static boolean matches(String actual,String expected){return expected==null||expected.isBlank()||"ALL".equalsIgnoreCase(expected)||(actual!=null&&actual.equalsIgnoreCase(expected));}
    private static boolean facetMatches(List<FacetView> values,String type,String expected){return expected==null||expected.isBlank()||values.stream().anyMatch(value->value.type().equals(type)&&(value.code().equalsIgnoreCase(expected)||normalize(value.label()).contains(normalize(expected))));}
    private static String normalize(String value){return value==null?"":value.toLowerCase(Locale.ROOT).trim();}
    private FacetView facetView(ResumeTemplateCatalogFacetEntity value){return new FacetView(value.getCatalogEntryId(),value.getFacetType(),value.getFacetCode(),value.getFacetLabel());}
    private CatalogItemView view(ResumeTemplateCatalogEntryEntity value,List<FacetView> entryFacets,boolean includePreviewPages){
        boolean docx="DOCX_ASSET".equals(value.getEntryType());
        String previewStatus=docx?(value.getPreviewStatus()==null?"PENDING":value.getPreviewStatus()):"NOT_APPLICABLE";
        String previewVersion=docx&&"READY".equals(previewStatus)
                ?UriUtils.encodeQueryParam(value.getPreviewRendererVersion(),StandardCharsets.UTF_8):null;
        String thumbnail=docx
                ?("READY".equals(previewStatus)?"/api/v1/template-catalog/"+value.getId()+"/thumbnail?v="+previewVersion:null)
                :value.getThumbnailUri();
        List<PreviewPageView> pages=includePreviewPages&&docx&&"READY".equals(previewStatus)
                ?java.util.stream.IntStream.rangeClosed(1,value.getPreviewPageCount())
                        .mapToObj(page->new PreviewPageView(page,"/api/v1/template-catalog/"+value.getId()
                                +"/preview-pages/"+page+"?v="+previewVersion)).toList()
                :List.of();
        return new CatalogItemView(value.getId(),value.getEntryType(),value.getReferenceId(),value.getTitle(),value.getSummary(),
                value.getCapability(),value.getAssetKind(),value.getLanguageCode(),value.getPageCount(),value.getPhotoPolicy(),
                thumbnail,value.getSourceName(),value.getSourceUri(),value.getAttribution(),value.getDownloadCount(),entryFacets,
                value.getPublishedAt(),previewStatus,value.getPreviewPageCount(),pages,value.getPreviewError());
    }

    public static final class CatalogQuery extends PageQuery {
        private String keyword,capability,assetKind="RESUME",occupation,jobTag,style,language,pages,photoPolicy,careerStage;
        public String getKeyword(){return keyword;} public void setKeyword(String v){keyword=v;}
        public String getCapability(){return capability;} public void setCapability(String v){capability=v;}
        public String getAssetKind(){return assetKind;} public void setAssetKind(String v){assetKind=v;}
        public String getOccupation(){return occupation;} public void setOccupation(String v){occupation=v;}
        public String getJobTag(){return jobTag;} public void setJobTag(String v){jobTag=v;}
        public String getStyle(){return style;} public void setStyle(String v){style=v;}
        public String getLanguage(){return language;} public void setLanguage(String v){language=v;}
        public String getPages(){return pages;} public void setPages(String v){pages=v;}
        public String getPhotoPolicy(){return photoPolicy;} public void setPhotoPolicy(String v){photoPolicy=v;}
        public String getCareerStage(){return careerStage;} public void setCareerStage(String v){careerStage=v;}
    }
    public record CatalogItemView(String id,String entryType,String referenceId,String title,String summary,
            String capability,String assetKind,String languageCode,String pageCount,String photoPolicy,
            String thumbnailUri,String sourceName,String sourceUri,String attribution,long downloadCount,
            List<FacetView> facets,Instant publishedAt,String previewStatus,int previewPageCount,
            List<PreviewPageView> previewPages,String previewError) {}
    public record PreviewPageView(int pageNumber,String imageUri) {}
    public record FacetView(String catalogEntryId,String type,String code,String label) {}
    public record FacetGroupView(String type,List<FacetOptionView> options) {}
    public record FacetOptionView(String code,String label,long count) {}
    public record DownloadView(String filename,String fileHash,byte[] body) {}
    public record PreviewImageView(String etag,byte[] body) {}
    public record CatalogAdminItemView(CatalogItemView item,String publicationStatus,Instant retiredAt,Instant updatedAt,int version) {}
    public record CatalogMetadataCommand(String title,String summary,String assetKind,String languageCode,String pageCount,
            String photoPolicy,String thumbnailUri,List<CatalogFacetCommand> facets) {}
    public record CatalogFacetCommand(String type,String code,String label) {}
    private record ScoredEntry(ResumeTemplateCatalogEntryEntity entry,List<FacetView> facets,int score) {}
    private record FacetCounter(String code,String label,long count) {}
    private record CatalogSnapshot(List<ResumeTemplateCatalogEntryEntity> entries,
            Map<String,List<FacetView>> facetsByEntry) {}
}
