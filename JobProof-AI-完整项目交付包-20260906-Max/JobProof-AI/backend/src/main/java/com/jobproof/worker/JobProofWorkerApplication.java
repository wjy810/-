package com.jobproof.worker;

import com.jobproof.JobProofApiApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 独立 Worker 进程骨架。S0 默认在 API 进程内启用 in-process worker，不必单独启动本入口。
 */
public class JobProofWorkerApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(JobProofApiApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("worker")
                .run(args);
    }
}
