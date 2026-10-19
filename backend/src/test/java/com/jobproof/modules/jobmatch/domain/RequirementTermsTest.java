package com.jobproof.modules.jobmatch.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequirementTermsTest {

    @Test
    void pullsLatinTokensAndChinesePhrasesOutOfAJdSentence() {
        assertThat(RequirementTerms.of("熟练使用SQL进行数据查询与分析")).containsExactly("sql", "数据查询", "分析");
        assertThat(RequirementTerms.of("掌握 Python、C++ 或 Node.js 中的至少一种")).contains("python", "c++", "node.js");
        assertThat(RequirementTerms.of("3年以上互联网产品经验")).contains("互联网产品");
        assertThat(RequirementTerms.of("  ")).isEmpty();
    }

    @Test
    void latinTermsMatchWholeWordsOnly() {
        String resume = "负责用 javascript 和 mysql 搭建报表，参与数据查询优化";
        assertThat(RequirementTerms.present("java", resume)).isFalse();
        assertThat(RequirementTerms.present("javascript", resume)).isTrue();
        assertThat(RequirementTerms.present("sql", resume)).isFalse();
        assertThat(RequirementTerms.present("数据查询", resume)).isTrue();
    }
}
