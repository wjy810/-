package com.jobproof.modules.resume.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "resume_template_slots")
public class ResumeTemplateSlotEntity {
    @Id private String id;
    @Column(name = "template_version_id") private String templateVersionId;
    @Column(name = "slot_key") private String slotKey;
    @Column(name = "display_order") private int displayOrder;
    @Column(name = "capacity_units") private int capacityUnits;
    private boolean repeatable;
    @Column(name = "hide_when_empty") private boolean hideWhenEmpty;
    @Column(name = "overflow_strategy") private String overflowStrategy;
    @Column(name = "token_json", columnDefinition = "TEXT") private String tokenJson;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getTemplateVersionId(){return templateVersionId;} public void setTemplateVersionId(String v){templateVersionId=v;}
    public String getSlotKey(){return slotKey;} public void setSlotKey(String v){slotKey=v;}
    public int getDisplayOrder(){return displayOrder;} public void setDisplayOrder(int v){displayOrder=v;}
    public int getCapacityUnits(){return capacityUnits;} public void setCapacityUnits(int v){capacityUnits=v;}
    public boolean isRepeatable(){return repeatable;} public void setRepeatable(boolean v){repeatable=v;}
    public boolean isHideWhenEmpty(){return hideWhenEmpty;} public void setHideWhenEmpty(boolean v){hideWhenEmpty=v;}
    public String getOverflowStrategy(){return overflowStrategy;} public void setOverflowStrategy(String v){overflowStrategy=v;}
    public String getTokenJson(){return tokenJson;} public void setTokenJson(String v){tokenJson=v;}
}
