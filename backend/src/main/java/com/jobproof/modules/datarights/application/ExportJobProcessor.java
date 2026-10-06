package com.jobproof.modules.datarights.application;

import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.modules.task.infra.AsyncTaskEntity;
import com.jobproof.shared.security.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExportJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(ExportJobProcessor.class);

    private final TaskService taskService;
    private final DataRightsService dataRightsService;
    private final AccountJpaRepository accounts;
    private final ObjectStoragePort storage;
    private final NotificationService notificationService;

    public ExportJobProcessor(
            TaskService taskService,
            DataRightsService dataRightsService,
            AccountJpaRepository accounts,
            ObjectStoragePort storage,
            NotificationService notificationService) {
        this.taskService = taskService;
        this.dataRightsService = dataRightsService;
        this.accounts = accounts;
        this.storage = storage;
        this.notificationService = notificationService;
    }

    public void processDue() {
        taskService.claimNext(TaskTypes.ACCOUNT_EXPORT).ifPresent(this::run);
    }

    private void run(AsyncTaskEntity task) {
        try {
            AccountEntity account = accounts.findById(task.getAccountId()).orElseThrow();
            String objectKey = "exports/" + account.getId() + "/" + task.getId() + ".json";
            byte[] payload = dataRightsService.exportBytes(account.getId(), account.getEmail());
            storage.put(objectKey, payload);
            if (!taskService.stillRunning(task.getId())) {
                return;
            }
            String downloadToken = Tokens.randomToken();
            dataRightsService.completeExportFile(task.getId(), objectKey, downloadToken);
            taskService.markSucceeded(task.getId(), "export-v1", "{\"objectKey\":\"" + objectKey + "\"}");
            try {
                notificationService.request(
                        account.getId(),
                        NotificationType.TASK_COMPLETED,
                        task.getId() + ":SUCCEEDED",
                        "导出任务已完成",
                        "下载链接限时有效，过期需重新导出。通知不含导出正文。");
            } catch (RuntimeException e) {
                log.warn("export notification failed, task remains succeeded taskId={}", task.getId());
            }
        } catch (RuntimeException e) {
            log.warn("export job failed taskId={}", task.getId(), e);
            taskService.markFailed(task.getId(), "导出处理失败");
            try {
                notificationService.request(
                        task.getAccountId(),
                        NotificationType.TASK_FAILED,
                        task.getId() + ":FAILED",
                        "导出任务失败",
                        "数据导出没有完成，请到“数据与隐私”页面重新发起。");
            } catch (RuntimeException ignored) {
                // 通知失败不回滚任务状态
            }
        }
    }
}
