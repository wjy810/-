package com.jobproof.shared.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Something a module keeps for a signed-in user per browser — 投递追踪's mark that a browser is the job seeker's
 * own, say — and drops when the user signs out there. Identity calls every listener without knowing what they
 * keep; a listener that fails does not stop the sign-out.
 */
public interface SignOutListener {

    /** The user signed out of this browser. */
    void signedOut(String accountId, HttpServletRequest request, HttpServletResponse response);

    /**
     * Sessions on the user's other browsers ended (退出其他设备, or every session after a password change);
     * {@code includingThis}: this browser was signed out as well.
     */
    void otherBrowsersSignedOut(String accountId, boolean includingThis, HttpServletRequest request,
            HttpServletResponse response);
}
