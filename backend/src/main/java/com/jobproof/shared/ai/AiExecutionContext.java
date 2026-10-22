package com.jobproof.shared.ai;

/** Server-validated request metadata. Explicit scopes also carry it across worker boundaries. */
public final class AiExecutionContext {
    private static final ThreadLocal<State> LOCAL = new ThreadLocal<>();
    private AiExecutionContext() {}
    public record State(String quoteId, String path, String payloadHash, boolean paymentConfirmed,
            String accountId, String reservationId, String personalChannelId, String taskId, String operationId) {
        public State(String quoteId, String path, String payloadHash, boolean paymentConfirmed,
                String accountId, String reservationId, String personalChannelId, String taskId) {
            this(quoteId,path,payloadHash,paymentConfirmed,accountId,reservationId,personalChannelId,taskId,null);
        }
        public State(String quoteId, String path, String payloadHash, boolean paymentConfirmed,
                String accountId, String reservationId, String personalChannelId) {
            this(quoteId, path, payloadHash, paymentConfirmed, accountId, reservationId, personalChannelId, null);
        }
    }
    public static State current() { return LOCAL.get(); }
    public static void set(State state) { if (state == null) LOCAL.remove(); else LOCAL.set(state); }
    public static void clear() { LOCAL.remove(); }
    public static Scope use(State state) { State previous=current(); set(state); return () -> set(previous); }
    public static void reservation(String account, String id, String channel) {
        State s=current(); set(new State(s==null?null:s.quoteId(), s==null?null:s.path(), s==null?null:s.payloadHash(),
                s!=null&&s.paymentConfirmed(), account, id, channel, s==null?null:s.taskId(), s==null?null:s.operationId()));
    }
    public interface Scope extends AutoCloseable { @Override void close(); }
}
