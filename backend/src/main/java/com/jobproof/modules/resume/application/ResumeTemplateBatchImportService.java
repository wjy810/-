package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.resume.application.TemplateAssetMalwareScanner.ScanResult;
import com.jobproof.modules.resume.domain.ResumeTemplateCatalogClassifier;
import com.jobproof.modules.resume.domain.ResumeTemplateCatalogClassifier.Classification;
import com.jobproof.modules.resume.domain.ResumeTemplateDocxInspector;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogFacetEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogFacetJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateEvidenceEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateEvidenceJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateImportBatchEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateImportBatchJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateImportItemEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateImportItemJpaRepository;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import com.jobproof.shared.time.ClockPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ResumeTemplateBatchImportService {
    private static final Logger log = LoggerFactory.getLogger(ResumeTemplateBatchImportService.class);
    public static final String SOURCE_CODE="HICV-GITHUB-2026-2132";
    public static final String SOURCE_URI="https://github.com/HICV-CN/hicv-word-resume-templates";
    private static final String DOCX_TYPE="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> ACTIVE=Set.of("QUEUED","RUNNING");
    private final Path sourceRoot;
    private final Path licenseFile;
    private final ResumeTemplateImportBatchJpaRepository batches;
    private final ResumeTemplateImportItemJpaRepository items;
    private final ResumeTemplateAssetJpaRepository assets;
    private final ResumeTemplateCatalogEntryJpaRepository catalog;
    private final ResumeTemplateCatalogFacetJpaRepository facets;
    private final ResumeTemplateEvidenceJpaRepository evidence;
    private final ResumeSmartTemplateCatalogPublisher smartCatalogPublisher;
    private final ResumeTemplateCatalogService catalogService;
    private final ObjectStoragePort storage;
    private final TemplateAssetMalwareScanner malwareScanner;
    private final AuditService audit;
    private final ClockPort clock;
    private final ObjectMapper mapper;

    public ResumeTemplateBatchImportService(
            @Value("${jobproof.templates.catalog-source-dir:../assets/resume-templates/templates}") String sourceRoot,
            @Value("${jobproof.templates.catalog-license-file:../assets/resume-templates/LICENSE}") String licenseFile,
            ResumeTemplateImportBatchJpaRepository batches, ResumeTemplateImportItemJpaRepository items,
            ResumeTemplateAssetJpaRepository assets, ResumeTemplateCatalogEntryJpaRepository catalog,
            ResumeTemplateCatalogFacetJpaRepository facets, ResumeTemplateEvidenceJpaRepository evidence,
            ResumeSmartTemplateCatalogPublisher smartCatalogPublisher,
            ResumeTemplateCatalogService catalogService,
            ObjectStoragePort storage, TemplateAssetMalwareScanner malwareScanner, AuditService audit,
            ClockPort clock, ObjectMapper mapper) {
        this.sourceRoot=Path.of(sourceRoot).toAbsolutePath().normalize();
        this.licenseFile=Path.of(licenseFile).toAbsolutePath().normalize();
        this.batches=batches; this.items=items; this.assets=assets; this.catalog=catalog; this.facets=facets;
        this.evidence=evidence; this.smartCatalogPublisher=smartCatalogPublisher; this.catalogService=catalogService;
        this.storage=storage; this.malwareScanner=malwareScanner; this.audit=audit; this.clock=clock; this.mapper=mapper;
    }

    @Transactional
    public BatchView start(CurrentAccount current) {
        assertAdmin(current);
        if (!Files.isDirectory(sourceRoot)) throw AppException.conflict("TEMPLATE_IMPORT_SOURCE_MISSING","固定模板源目录不存在");
        ResumeTemplateImportBatchEntity existing=batches.findBySourceCode(SOURCE_CODE).orElse(null);
        if (existing!=null) {
            if (Set.of("PAUSED","FAILED").contains(existing.getStatus())) { existing.setStatus("QUEUED"); existing.setVersionNo(existing.getVersionNo()+1); existing.setUpdatedAt(clock.now()); batches.save(existing); }
            return view(existing);
        }
        ResumeTemplateEvidenceEntity license=ensureLicenseEvidence(current);
        List<Path> files;
        try(var stream=Files.walk(sourceRoot)) {
            files=stream.filter(Files::isRegularFile).filter(path->path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx"))
                    .sorted(Comparator.comparing(path->relative(path).toLowerCase(Locale.ROOT))).toList();
        } catch(Exception exception) { throw AppException.conflict("TEMPLATE_IMPORT_INVENTORY_FAILED","无法盘点固定模板源目录"); }
        Instant now=clock.now();
        ResumeTemplateImportBatchEntity batch=new ResumeTemplateImportBatchEntity();
        batch.setId(Ids.newId()); batch.setSourceCode(SOURCE_CODE); batch.setSourceName("HICV Word Resume Templates");
        batch.setSourceUri(SOURCE_URI); batch.setLicenseEvidenceId(license.getId()); batch.setDistributionMode("NON_COMMERCIAL");
        batch.setStatus("QUEUED"); batch.setTotalCount(files.size()); batch.setRequestedBy(current.accountId());
        batch.setCreatedAt(now); batch.setUpdatedAt(now); batch.setVersionNo(0); batches.save(batch);
        for(Path file:files) {
            ResumeTemplateImportItemEntity item=new ResumeTemplateImportItemEntity();
            String relativePath=relative(file);
            item.setId(Ids.newId()); item.setBatchId(batch.getId()); item.setSourceRelativePath(relativePath);
            item.setSourcePathHash(ResumeTemplateDocxInspector.sha256(relativePath.getBytes(StandardCharsets.UTF_8)));
            item.setOriginalFilename(file.getFileName().toString());
            try { item.setSizeBytes(Files.size(file)); } catch(Exception exception) { item.setSizeBytes(0); item.setDetailCode("FILE_STAT_FAILED"); }
            item.setStatus("INVENTORIED"); item.setCreatedAt(now); item.setUpdatedAt(now); items.save(item);
        }
        for (String templateId : List.of("rlt-b-ats-minimal-v1", "rlt-b-tech-double-v1", "rlt-b-cn-table-v1")) {
            smartCatalogPublisher.sync(templateId);
        }
        audit.append(current.accountId(),"TEMPLATE_IMPORT_BATCH_STARTED","RESUME_TEMPLATE_IMPORT_BATCH",batch.getId(),"source="+SOURCE_CODE+" total="+files.size()+" distribution=NON_COMMERCIAL");
        return view(batch);
    }

    @Transactional(readOnly=true)
    public PageResult<BatchView> batches(CurrentAccount current,PageQuery query) {
        assertAdmin(current);
        var page=batches.findAllByOrderByCreatedAtDesc(PageRequest.of(query.page(),query.size()));
        return new PageResult<>(page.getContent().stream().map(this::view).toList(),page.getTotalElements(),query.page(),query.size());
    }

    @Transactional(readOnly=true)
    public PageResult<ItemView> items(CurrentAccount current,String batchId,PageQuery query) {
        assertAdmin(current); requireBatch(batchId);
        var page=items.findByBatchIdOrderBySourceRelativePathAsc(batchId,PageRequest.of(query.page(),query.size()));
        return new PageResult<>(page.getContent().stream().map(this::view).toList(),page.getTotalElements(),query.page(),query.size());
    }

    @Transactional
    public BatchView pause(CurrentAccount current,String batchId) {
        assertAdmin(current); ResumeTemplateImportBatchEntity batch=requireBatch(batchId);
        if (Set.of("QUEUED","RUNNING").contains(batch.getStatus())) {batch.setStatus("PAUSED");batch.setVersionNo(batch.getVersionNo()+1);batch.setUpdatedAt(clock.now());batches.save(batch);}
        return view(batch);
    }

    @Transactional
    public BatchView retry(CurrentAccount current,String batchId) {
        assertAdmin(current); ResumeTemplateImportBatchEntity batch=requireBatch(batchId);
        for(ResumeTemplateImportItemEntity item:items.findByBatchId(batchId)) {
            if (Set.of("QUARANTINED","ERROR").contains(item.getStatus())) {item.setStatus("INVENTORIED");item.setDetailCode(null);item.setUpdatedAt(clock.now());items.save(item);}
        }
        batch.setStatus("QUEUED"); batch.setErrorMessage(null); batch.setCompletedAt(null); batch.setVersionNo(batch.getVersionNo()+1); batch.setUpdatedAt(clock.now()); batches.save(batch);
        return view(batch);
    }

    @Transactional
    public void processDue() {
        ResumeTemplateImportBatchEntity batch=batches.findFirstByStatusInOrderByCreatedAtAsc(ACTIVE).orElse(null);
        if(batch==null)return;
        if("QUEUED".equals(batch.getStatus())) {batch.setStatus("RUNNING");batch.setStartedAt(clock.now());batches.save(batch);}
        for(int count=0;count<8;count++) {
            ResumeTemplateImportItemEntity item=items.findFirstByBatchIdAndStatusOrderBySourceRelativePathAsc(batch.getId(),"INVENTORIED").orElse(null);
            if(item==null)break;
            processItem(batch,item);
        }
        refresh(batch);
    }

    private void processItem(ResumeTemplateImportBatchEntity batch,ResumeTemplateImportItemEntity item) {
        Instant now=clock.now(); item.setStatus("SCANNING"); item.setUpdatedAt(now); items.save(item);
        Path file=sourceRoot.resolve(item.getSourceRelativePath()).normalize();
        if(!file.startsWith(sourceRoot)||!Files.isRegularFile(file)){finish(item,"ERROR","SOURCE_FILE_MISSING",null,null);return;}
        if(item.getSizeBytes()>ResumeTemplateDocxInspector.MAX_BATCH_BYTES){finish(item,"UNSUPPORTED","FILE_TOO_LARGE",null,null);return;}
        byte[] content;
        try{content=Files.readAllBytes(file);}catch(Exception exception){finish(item,"ERROR","FILE_READ_FAILED",null,null);return;}
        String hash=ResumeTemplateDocxInspector.sha256(content); item.setFileHash(hash);
        ResumeTemplateAssetEntity existing=assets.findByFileHash(hash).orElse(null);
        if(existing!=null&&!Objects.equals(existing.getId(),item.getAssetId())){finish(item,"DUPLICATE","DUPLICATE_HASH",null,existing.getId());return;}
        ResumeTemplateDocxInspector.Inspection inspection=ResumeTemplateDocxInspector.inspect(content,ResumeTemplateDocxInspector.MAX_BATCH_BYTES);
        ScanResult malware=inspection.accepted()?malwareScanner.scan(content):ScanResult.notRun();
        ResumeTemplateAssetEntity asset=asset(existing,batch,item,hash,content.length,inspection,malware,now);
        assets.save(asset); item.setAssetId(asset.getId());
        if(!inspection.accepted()){finish(item,"REJECTED",firstFinding(inspection),asset.getId(),null);return;}
        if(malware.outcome()==TemplateAssetMalwareScanner.Outcome.INFECTED){finish(item,"REJECTED","MALWARE_DETECTED",asset.getId(),null);return;}
        if(malware.outcome()!=TemplateAssetMalwareScanner.Outcome.CLEAN){
            String key="template-quarantine/"+asset.getId()+"/"+hash+".docx"; moveStoredObject(asset.getStorageKey(),key,content);asset.setStorageKey(key);assets.save(asset);
            finish(item,"QUARANTINED",malware.detailCode(),asset.getId(),null);return;
        }
        String key="template-assets/"+asset.getId()+"/"+hash+".docx"; moveStoredObject(asset.getStorageKey(),key,content); asset.setStorageKey(key);assets.save(asset);
        upsertCatalog(asset,item,now); finish(item,"READY","READY",asset.getId(),null);
    }

    private ResumeTemplateAssetEntity asset(ResumeTemplateAssetEntity existing,ResumeTemplateImportBatchEntity batch,ResumeTemplateImportItemEntity item,String hash,long bytes,
            ResumeTemplateDocxInspector.Inspection inspection,ScanResult malware,Instant now) {
        ResumeTemplateAssetEntity asset=existing==null?new ResumeTemplateAssetEntity():existing;
        if(existing==null){asset.setId(Ids.newId());asset.setCreatedAt(now);}
        asset.setSourceName(stripExtension(item.getOriginalFilename())); asset.setOriginalFilename(item.getOriginalFilename());
        asset.setSourceRelativePath(item.getSourceRelativePath()); asset.setImportBatchId(batch.getId());
        asset.setSourceUri(SOURCE_URI+"/blob/main/templates/"+encodePath(item.getSourceRelativePath()));
        asset.setLicenseStatus("APPROVED"); asset.setLicenseEvidenceId(batch.getLicenseEvidenceId()); asset.setFileHash(hash);
        asset.setContentType(DOCX_TYPE); asset.setSizeBytes(bytes); asset.setUploadedBy(batch.getRequestedBy()); asset.setScannedAt(now);
        boolean clean=inspection.accepted()&&malware.outcome()==TemplateAssetMalwareScanner.Outcome.CLEAN;
        boolean rejected=!inspection.accepted()||malware.outcome()==TemplateAssetMalwareScanner.Outcome.INFECTED;
        asset.setScanStatus(!inspection.accepted()?"STATIC_REJECTED":clean?"PASSED":malware.outcome()==TemplateAssetMalwareScanner.Outcome.INFECTED?"MALWARE_DETECTED":"MALWARE_SCANNER_UNAVAILABLE");
        asset.setStatus(rejected?"REJECTED":clean?"APPROVED":"REVIEWING"); asset.setRejectionReason(rejected?(!inspection.accepted()?firstFinding(inspection):"MALWARE_DETECTED"):clean?null:malware.detailCode());
        try{asset.setScanReportJson(mapper.writeValueAsString(Map.of("staticAccepted",inspection.accepted(),"scannerVersion",ResumeTemplateDocxInspector.SCANNER_VERSION,"entryCount",inspection.entryCount(),"uncompressedBytes",inspection.uncompressedBytes(),"findings",inspection.findings(),"malwareOutcome",malware.outcome().name(),"malwareEngine",malware.engine()==null?"":malware.engine(),"detailCode",malware.detailCode()==null?"":malware.detailCode())));}catch(Exception exception){asset.setScanReportJson("{\"detailCode\":\"REPORT_SERIALIZATION_FAILED\"}");}
        asset.setVersionNo(existing==null?0:asset.getVersionNo()+1); asset.setUpdatedAt(now); return asset;
    }

    private void upsertCatalog(ResumeTemplateAssetEntity asset,ResumeTemplateImportItemEntity item,Instant now) {
        Classification classification=ResumeTemplateCatalogClassifier.classify(item.getSourceRelativePath(),item.getOriginalFilename());
        ResumeTemplateCatalogEntryEntity entry=catalog.findByEntryTypeAndReferenceId("DOCX_ASSET",asset.getId()).orElseGet(ResumeTemplateCatalogEntryEntity::new);
        if(entry.getId()==null){entry.setId(Ids.newId());entry.setCreatedAt(now);entry.setVersionNo(0);} entry.setEntryType("DOCX_ASSET"); entry.setReferenceId(asset.getId());
        entry.setTitle(stripExtension(item.getOriginalFilename())); entry.setSummary("可下载的 HICV Word 模板，下载后可使用 Word 或 WPS 编辑。");
        entry.setCapability("DOCX_DOWNLOAD"); entry.setAssetKind(classification.assetKind()); entry.setLanguageCode(classification.languageCode());
        entry.setPageCount(classification.pageCount()); entry.setPhotoPolicy(classification.photoPolicy()); entry.setSourceName("HICV.cn"); entry.setSourceUri(asset.getSourceUri());
        entry.setAttribution("来源：HICV.cn；限个人、求职、教育及非商业模板分发使用。"); entry.setSearchText(classification.searchText());
        entry.setThumbnailUri(null); entry.setPreviewStatus("PENDING"); entry.setPreviewPageCount(0);
        entry.setPreviewError(null); entry.setPreviewRendererVersion(null); entry.setPreviewUpdatedAt(now);
        entry.setPublicationStatus("PUBLISHED"); entry.setPublishedAt(now); entry.setDownloadCount(0); entry.setUpdatedAt(now); catalog.save(entry);
        facets.deleteByCatalogEntryId(entry.getId());
        facets.flush();
        for(var classified:classification.facets()){ResumeTemplateCatalogFacetEntity facet=new ResumeTemplateCatalogFacetEntity();facet.setId(Ids.newId());facet.setCatalogEntryId(entry.getId());facet.setFacetType(classified.type());facet.setFacetCode(classified.code());facet.setFacetLabel(classified.label());facets.save(facet);}
        catalogService.invalidatePublicSnapshot();
    }

    private ResumeTemplateEvidenceEntity ensureLicenseEvidence(CurrentAccount current) {
        byte[] content;
        try{content=Files.readAllBytes(licenseFile);}catch(Exception exception){throw AppException.conflict("TEMPLATE_LICENSE_FILE_MISSING","HICV LICENSE 文件不存在");}
        String hash=ResumeTemplateDocxInspector.sha256(content);
        ResumeTemplateEvidenceEntity existing=evidence.findByFileHash(hash).orElse(null);if(existing!=null)return existing;
        ResumeTemplateEvidenceEntity value=new ResumeTemplateEvidenceEntity();Instant now=clock.now();value.setId(Ids.newId());value.setEvidenceType("LICENSE");value.setOriginalFilename("HICV-LICENSE.txt");value.setContentType("text/plain;charset=UTF-8");value.setSizeBytes(content.length);value.setFileHash(hash);value.setDescription("HICV GitHub LICENSE snapshot; NON_COMMERCIAL redistribution only");value.setUploadedBy(current.accountId());value.setCreatedAt(now);String key="template-evidence/"+value.getId()+"/"+hash+".txt";storage.put(key,content);registerRollbackCleanup(key);value.setStorageKey(key);evidence.save(value);audit.append(current.accountId(),"TEMPLATE_EVIDENCE_UPLOADED","RESUME_TEMPLATE_EVIDENCE",value.getId(),"type=LICENSE hash="+hash.substring(0,12));return value;
    }

    private void finish(ResumeTemplateImportItemEntity item,String status,String detail,String assetId,String duplicateId){item.setStatus(status);item.setDetailCode(detail);if(assetId!=null)item.setAssetId(assetId);if(duplicateId!=null)item.setDuplicateAssetId(duplicateId);item.setUpdatedAt(clock.now());items.save(item);}
    private void refresh(ResumeTemplateImportBatchEntity batch){List<ResumeTemplateImportItemEntity> all=items.findByBatchId(batch.getId());batch.setProcessedCount((int)all.stream().filter(item->!Set.of("INVENTORIED","SCANNING").contains(item.getStatus())).count());batch.setReadyCount(count(all,"READY"));batch.setDuplicateCount(count(all,"DUPLICATE"));batch.setRejectedCount(count(all,"REJECTED"));batch.setQuarantinedCount(count(all,"QUARANTINED"));batch.setUnsupportedCount(count(all,"UNSUPPORTED"));if(batch.getProcessedCount()>=batch.getTotalCount()){batch.setStatus("COMPLETED");batch.setCompletedAt(clock.now());}batch.setUpdatedAt(clock.now());batch.setVersionNo(batch.getVersionNo()+1);batches.save(batch);}
    private static int count(List<ResumeTemplateImportItemEntity> all,String status){return(int)all.stream().filter(item->status.equals(item.getStatus())).count();}
    private void moveStoredObject(String previousKey,String nextKey,byte[] content){if(Objects.equals(previousKey,nextKey))return;storage.put(nextKey,content);registerRollbackCleanup(nextKey);if(previousKey!=null)registerCommitCleanup(previousKey);}
    private void registerRollbackCleanup(String objectKey){TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status==TransactionSynchronization.STATUS_COMMITTED)return;try{storage.delete(objectKey);}catch(RuntimeException exception){log.warn("template batch rollback cleanup failed objectKey={}",objectKey,exception);}}});}
    private void registerCommitCleanup(String objectKey){TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){try{storage.delete(objectKey);}catch(RuntimeException exception){log.warn("template quarantine cleanup failed objectKey={}",objectKey,exception);}}});}
    private ResumeTemplateImportBatchEntity requireBatch(String id){return batches.findById(id).orElseThrow(()->AppException.user("TEMPLATE_IMPORT_BATCH_NOT_FOUND","导入批次不存在"));}
    private String relative(Path path){return sourceRoot.relativize(path.toAbsolutePath().normalize()).toString().replace('\\','/');}
    private static String stripExtension(String value){return value.toLowerCase(Locale.ROOT).endsWith(".docx")?value.substring(0,value.length()-5):value;}
    private static String firstFinding(ResumeTemplateDocxInspector.Inspection value){return value.findings().isEmpty()?"STATIC_REJECTED":value.findings().get(0).code();}
    private static String encodePath(String value){return java.util.Arrays.stream(value.split("/")).map(part->URLEncoder.encode(part,StandardCharsets.UTF_8).replace("+","%20")).collect(java.util.stream.Collectors.joining("/"));}
    private static void assertAdmin(CurrentAccount current){if(current==null||!"ADMIN".equals(current.role()))throw AppException.forbidden("ADMIN_ONLY","仅管理员可以管理模板导入");}
    private BatchView view(ResumeTemplateImportBatchEntity value){return new BatchView(value.getId(),value.getSourceCode(),value.getSourceName(),value.getSourceUri(),value.getDistributionMode(),value.getStatus(),value.getTotalCount(),value.getProcessedCount(),value.getReadyCount(),value.getDuplicateCount(),value.getRejectedCount(),value.getQuarantinedCount(),value.getUnsupportedCount(),value.getErrorMessage(),value.getStartedAt(),value.getCompletedAt(),value.getCreatedAt(),value.getUpdatedAt(),value.getVersionNo());}
    private ItemView view(ResumeTemplateImportItemEntity value){return new ItemView(value.getId(),value.getSourceRelativePath(),value.getOriginalFilename(),value.getSizeBytes(),value.getFileHash(),value.getStatus(),value.getAssetId(),value.getDuplicateAssetId(),value.getDetailCode(),value.getUpdatedAt());}
    public record BatchView(String id,String sourceCode,String sourceName,String sourceUri,String distributionMode,String status,int totalCount,int processedCount,int readyCount,int duplicateCount,int rejectedCount,int quarantinedCount,int unsupportedCount,String errorMessage,Instant startedAt,Instant completedAt,Instant createdAt,Instant updatedAt,int version){}
    public record ItemView(String id,String sourceRelativePath,String originalFilename,long sizeBytes,String fileHash,String status,String assetId,String duplicateAssetId,String detailCode,Instant updatedAt){}
}
