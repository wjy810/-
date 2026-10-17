package com.jobproof.modules.notification.application;

import com.jobproof.modules.task.domain.TaskRefs;
import com.jobproof.modules.task.domain.TaskTypes;
import java.util.Map;
import java.util.Optional;

/**
 * What a finished background task tells the user: a plain-language title and body, plus a link to
 * the page where the result (or the way to retry) lives. Steps the user is watching live and tasks
 * whose module already notifies on its own return nothing.
 */
final class TaskNotificationCopy {

    record Copy(String title, String body, String actionPath) {
    }

    private TaskNotificationCopy() {
    }

    static Optional<Copy> of(String taskType, boolean succeeded, Map<String, String> refs) {
        String type = taskType == null ? "" : taskType;
        return Optional.ofNullable(switch (type) {
            case TaskTypes.ACCOUNT_EXPORT -> succeeded
                    ? new Copy("个人数据导出已完成", "导出文件已生成，请在有效期内下载；过期后需要重新导出。", "/account/data-rights")
                    : new Copy("个人数据导出失败", "这次导出没有完成，可以在“数据与隐私”页面重新发起。", "/account/data-rights");
            // Deletion progress has its own notifications (DATA_DELETION_PROGRESS).
            case TaskTypes.ACCOUNT_DELETION -> null;
            case TaskTypes.RESUME_PDF_EXPORT -> succeeded
                    ? new Copy("简历 PDF 已生成", "回到这份简历即可下载 PDF。", resumeVersionPath(refs))
                    : new Copy("简历 PDF 生成失败", "这次没有生成 PDF，回到简历重新导出即可，内容不受影响。", resumeVersionPath(refs));
            case TaskTypes.RESUME_DOCX_EXPORT -> succeeded
                    ? new Copy("简历 Word 文档已生成", "回到这份简历即可下载 Word 文档。", resumeVersionPath(refs))
                    : new Copy("简历 Word 文档生成失败", "这次没有生成 Word 文档，回到简历重新导出即可，内容不受影响。", resumeVersionPath(refs));
            case TaskTypes.CAREER_FILE_PROCESS -> succeeded
                    ? new Copy("资料文件已通过安全检查", "文件已可以预览，也能在简历和岗位匹配中引用。", "/career-library?view=files")
                    : new Copy("资料文件没有通过处理", "文件未通过安全检查或无法生成预览，打开资料库查看原因并重试。", "/career-library?view=files");
            // The import page polls the parse step and shows the result itself.
            case TaskTypes.RESUME_IMPORT_PARSE -> succeeded ? null
                    : new Copy("简历导入失败", "这份简历没有解析成功，可以换一个文件或直接粘贴文字重新导入。", "/ai-resume/new");
            case TaskTypes.JD_PARSE -> succeeded ? null
                    : new Copy("岗位描述解析失败", "没有读懂这份岗位描述，可以补充内容后重新分析。", matchPath(refs, "analyzing"));
            case TaskTypes.JOB_MATCH -> succeeded
                    ? new Copy("岗位匹配报告已生成", "查看匹配度、证据和补强建议。", matchPath(refs, "report"))
                    : new Copy("岗位匹配分析失败", "这次分析没有完成，打开后可以重新分析。", matchPath(refs, "analyzing"));
            case TaskTypes.MATCH_AI_ADVICE -> succeeded
                    ? new Copy("岗位匹配建议已生成", "补强建议已经整理好。", matchPath(refs, "report"))
                    : new Copy("岗位匹配建议生成失败", "这次没有生成建议，可以稍后在报告页重试。", matchPath(refs, "report"));
            case TaskTypes.JOB_MATCH_EXPORT -> succeeded
                    ? new Copy("匹配报告已导出", "回到报告页即可下载导出的文件。", matchPath(refs, "report"))
                    : new Copy("匹配报告导出失败", "这次导出没有完成，回到报告页重新导出即可。", matchPath(refs, "report"));
            case TaskTypes.CAREER_PLANNING_CANVAS -> succeeded
                    ? new Copy("能力画布已生成", "打开职业规划查看新的能力画布。", planningPath(refs))
                    : new Copy("能力画布生成失败", "这次没有生成画布，打开职业规划可以重新生成。", planningPath(refs));
            case TaskTypes.CAREER_PLANNING_CANVAS_PROPOSAL -> succeeded
                    ? new Copy("画布调整建议已生成", "确认后才会改动你的能力画布。", planningPath(refs))
                    : new Copy("画布调整建议生成失败", "这次没有生成调整建议，可以稍后重试。", planningPath(refs));
            // The planning interview runs in the conversation the user is looking at.
            case TaskTypes.CAREER_PLANNING_INTERVIEW -> succeeded ? null
                    : new Copy("职业规划访谈中断", "这一轮访谈没有完成，打开职业规划继续。", planningPath(refs));
            case TaskTypes.CAREER_PLANNING_RECOMMENDATIONS -> succeeded
                    ? new Copy("职业方向推荐已生成", "打开职业规划查看推荐的方向和理由。", planningPath(refs))
                    : new Copy("职业方向推荐生成失败", "这次没有生成推荐，可以稍后重试。", planningPath(refs));
            case TaskTypes.CAREER_PLANNING_VALIDATION_BATCH -> succeeded
                    ? new Copy("方向验证已完成", "验证结果已写回职业规划。", planningPath(refs))
                    : new Copy("方向验证没有完成", "这批验证没有完成，打开职业规划重新发起。", planningPath(refs));
            default -> succeeded
                    ? new Copy("后台任务已完成", "你发起的一项后台任务已经完成。", null)
                    : new Copy("后台任务未完成", "你发起的一项后台任务没有完成，可以回到原页面重试。", null);
        });
    }

    private static String resumeVersionPath(Map<String, String> refs) {
        String id = refs.get("resumeVersionId");
        return TaskRefs.isSafeId(id) ? "/resumes/versions/" + id : "/resumes";
    }

    private static String matchPath(Map<String, String> refs, String page) {
        String id = refs.get("matchId");
        return TaskRefs.isSafeId(id) ? "/job-match/" + id + "/" + page : "/job-match/history";
    }

    private static String planningPath(Map<String, String> refs) {
        String id = refs.get("sessionId");
        return TaskRefs.isSafeId(id) ? "/career-planning/" + id : "/career-planning";
    }
}
