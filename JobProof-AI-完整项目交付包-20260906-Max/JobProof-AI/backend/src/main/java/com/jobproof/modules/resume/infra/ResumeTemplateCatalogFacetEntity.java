package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "resume_template_catalog_facets")
public class ResumeTemplateCatalogFacetEntity {
    @Id private String id;
    @Column(name="catalog_entry_id") private String catalogEntryId;
    @Column(name="facet_type") private String facetType;
    @Column(name="facet_code") private String facetCode;
    @Column(name="facet_label") private String facetLabel;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getCatalogEntryId(){return catalogEntryId;} public void setCatalogEntryId(String v){catalogEntryId=v;}
    public String getFacetType(){return facetType;} public void setFacetType(String v){facetType=v;}
    public String getFacetCode(){return facetCode;} public void setFacetCode(String v){facetCode=v;}
    public String getFacetLabel(){return facetLabel;} public void setFacetLabel(String v){facetLabel=v;}
}

