package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResumeTemplateCatalogClassifierTest {
    @Test
    void emitsTheTwelveStableOccupationCodesAndSeparatesMaterialChannels() {
        assertOccupation("09_行业专属/前端开发工程师.docx","TECHNOLOGY");
        assertOccupation("09_行业专属/护士简历.docx","HEALTHCARE");
        assertOccupation("09_行业专属/财务会计.docx","FINANCE");
        assertOccupation("09_行业专属/教师求职.docx","EDUCATION_RESEARCH");
        assertOccupation("09_行业专属/建筑工程师.docx","CONSTRUCTION_ENGINEERING");
        assertOccupation("09_行业专属/物流采购.docx","LOGISTICS_TRANSPORT");
        assertThat(ResumeTemplateCatalogClassifier.classify("03_封面页/封面.docx","封面.docx").assetKind()).isEqualTo("COVER");
        assertThat(ResumeTemplateCatalogClassifier.classify("13_小升初自我介绍/材料.docx","材料.docx").assetKind()).isEqualTo("SCHOOL_APPLICATION");
        assertThat(ResumeTemplateCatalogClassifier.classify("15_自荐信与范文/自荐信.docx","自荐信.docx").assetKind()).isEqualTo("COVER_LETTER");
    }
    private static void assertOccupation(String path,String code){assertThat(ResumeTemplateCatalogClassifier.classify(path,path.substring(path.lastIndexOf('/')+1)).facets()).anySatisfy(value->{assertThat(value.type()).isEqualTo("OCCUPATION");assertThat(value.code()).isEqualTo(code);});}
}
