package com.jobproof.modules.jobapply.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ViewerAgentTest {

    @Test
    void readsTheDeviceAndTheAppAResumeWasOpenedIn() {
        assertThat(ViewerAgent.parse("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Mobile/15E148 MicroMessenger/8.0.50"))
                .isEqualTo(new ViewerAgent("MOBILE", "WECHAT", false));
        assertThat(ViewerAgent.parse("Mozilla/5.0 (Windows NT 10.0) Chrome/140.0 MicroMessenger/7.0 wxwork/4.1"))
                .isEqualTo(new ViewerAgent("DESKTOP", "WECOM", false));
        assertThat(ViewerAgent.parse("Mozilla/5.0 (Linux; Android 15) Mobile Safari/537.36 DingTalk/7.6"))
                .isEqualTo(new ViewerAgent("MOBILE", "DINGTALK", false));
        assertThat(ViewerAgent.parse("Mozilla/5.0 (Macintosh) AppleWebKit/605 Lark/7.30 Feishu"))
                .isEqualTo(new ViewerAgent("DESKTOP", "FEISHU", false));
        assertThat(ViewerAgent.parse("Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) Safari/604.1"))
                .isEqualTo(new ViewerAgent("TABLET", "BROWSER", false));
    }

    @Test
    void crawlersPreviewersAndEmptyAgentsAreNeverReaders() {
        assertThat(ViewerAgent.parse("Mozilla/5.0 (compatible; Googlebot/2.1)").bot()).isTrue();
        assertThat(ViewerAgent.parse("Slackbot-LinkExpanding 1.0").bot()).isTrue();
        assertThat(ViewerAgent.parse("curl/8.5.0").bot()).isTrue();
        assertThat(ViewerAgent.parse("Mozilla/5.0 HeadlessChrome/140.0").bot()).isTrue();
        assertThat(ViewerAgent.parse("").bot()).isTrue();
        assertThat(ViewerAgent.parse(null).bot()).isTrue();
    }

    @Test
    void theFurthestOpenStageIsKeptWhenAnApplicationMovesBackOrCloses() {
        assertThat(ApplicationStage.furthest(ApplicationStage.INTERVIEW, ApplicationStage.APPLIED)).isEqualTo(ApplicationStage.INTERVIEW);
        assertThat(ApplicationStage.furthest(ApplicationStage.INTERVIEW, ApplicationStage.CLOSED)).isEqualTo(ApplicationStage.INTERVIEW);
        assertThat(ApplicationStage.furthest(ApplicationStage.APPLIED, ApplicationStage.OFFER)).isEqualTo(ApplicationStage.OFFER);
    }
}
