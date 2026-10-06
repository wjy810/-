package com.jobproof.modules.jobmatch.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobMatchSimilarDirectionsTest {

    @Test
    void latinTermsMatchWholeSkillTokensOnly() {
        assertThat(JobMatchService.namesSkill("Python", "python")).isTrue();
        assertThat(JobMatchService.namesSkill("Java", "Java 开发")).isTrue();
        assertThat(JobMatchService.namesSkill("C++", "C++")).isTrue();
        assertThat(JobMatchService.namesSkill("Node.js", "Node.js")).isTrue();
        assertThat(JobMatchService.namesSkill("Go", "Django")).isFalse();
        assertThat(JobMatchService.namesSkill("Java", "JavaScript")).isFalse();
        assertThat(JobMatchService.namesSkill("C", "CSS")).isFalse();
    }

    @Test
    void chineseTermsMatchWhenTheSkillContainsThem() {
        assertThat(JobMatchService.namesSkill("数据挖掘", "数据挖掘与分析")).isTrue();
        assertThat(JobMatchService.namesSkill("视频剪辑", "视频剪辑")).isTrue();
        assertThat(JobMatchService.namesSkill("机器学习", "深度学习")).isFalse();
        assertThat(JobMatchService.namesSkill("会计", "沟通")).isFalse();
        assertThat(JobMatchService.namesSkill("", "Java")).isFalse();
    }
}
