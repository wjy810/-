package com.jobproof.shared.deletion;

/**
 * 各业务模块挂到 S0 账号删除编排的回执口。S1 起由拥有方自己清理，不得假装已删。
 */
public interface DeletionModuleHandler {

    String moduleCode();

    Result onAccountDeletion(String accountId);

    record Result(String status, String message) {
        public static Result succeeded(String message) {
            return new Result("SUCCEEDED", message);
        }

        public static Result failed(String message) {
            return new Result("FAILED", message);
        }

        public static Result restricted(String message) {
            return new Result("RESTRICTED", message);
        }
    }
}
