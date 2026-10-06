package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_template_catalog_entries")
public class ResumeTemplateCatalogEntryEntity {
    @Id private String id;
    @Column(name="entry_type") private String entryType;
    @Column(name="reference_id") private String referenceId;
    private String title;
    private String summary;
    private String capability;
    @Column(name="asset_kind") private String assetKind;
    @Column(name="language_code") private String languageCode;
    @Column(name="page_count") private String pageCount;
    @Column(name="photo_policy") private String photoPolicy;
    @Column(name="thumbnail_uri") private String thumbnailUri;
    @Column(name="preview_status") private String previewStatus;
    @Column(name="preview_page_count") private int previewPageCount;
    @Column(name="preview_error") private String previewError;
    @Column(name="preview_renderer_version") private String previewRendererVersion;
    @Column(name="preview_updated_at") private Instant previewUpdatedAt;
    @Column(name="source_name") private String sourceName;
    @Column(name="source_uri") private String sourceUri;
    private String attribution;
    @Column(name="search_text", columnDefinition="TEXT") private String searchText;
    @Column(name="publication_status") private String publicationStatus;
    @Column(name="download_count") private long downloadCount;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="retired_at") private Instant retiredAt;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="updated_at") private Instant updatedAt;
    @Column(name="version_no") private int versionNo;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getEntryType(){return entryType;} public void setEntryType(String v){entryType=v;}
    public String getReferenceId(){return referenceId;} public void setReferenceId(String v){referenceId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public String getCapability(){return capability;} public void setCapability(String v){capability=v;}
    public String getAssetKind(){return assetKind;} public void setAssetKind(String v){assetKind=v;}
    public String getLanguageCode(){return languageCode;} public void setLanguageCode(String v){languageCode=v;}
    public String getPageCount(){return pageCount;} public void setPageCount(String v){pageCount=v;}
    public String getPhotoPolicy(){return photoPolicy;} public void setPhotoPolicy(String v){photoPolicy=v;}
    public String getThumbnailUri(){return thumbnailUri;} public void setThumbnailUri(String v){thumbnailUri=v;}
    public String getPreviewStatus(){return previewStatus;} public void setPreviewStatus(String v){previewStatus=v;}
    public int getPreviewPageCount(){return previewPageCount;} public void setPreviewPageCount(int v){previewPageCount=v;}
    public String getPreviewError(){return previewError;} public void setPreviewError(String v){previewError=v;}
    public String getPreviewRendererVersion(){return previewRendererVersion;} public void setPreviewRendererVersion(String v){previewRendererVersion=v;}
    public Instant getPreviewUpdatedAt(){return previewUpdatedAt;} public void setPreviewUpdatedAt(Instant v){previewUpdatedAt=v;}
    public String getSourceName(){return sourceName;} public void setSourceName(String v){sourceName=v;}
    public String getSourceUri(){return sourceUri;} public void setSourceUri(String v){sourceUri=v;}
    public String getAttribution(){return attribution;} public void setAttribution(String v){attribution=v;}
    public String getSearchText(){return searchText;} public void setSearchText(String v){searchText=v;}
    public String getPublicationStatus(){return publicationStatus;} public void setPublicationStatus(String v){publicationStatus=v;}
    public long getDownloadCount(){return downloadCount;} public void setDownloadCount(long v){downloadCount=v;}
    public Instant getPublishedAt(){return publishedAt;} public void setPublishedAt(Instant v){publishedAt=v;}
    public Instant getRetiredAt(){return retiredAt;} public void setRetiredAt(Instant v){retiredAt=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
    public int getVersionNo(){return versionNo;} public void setVersionNo(int v){versionNo=v;}
}
