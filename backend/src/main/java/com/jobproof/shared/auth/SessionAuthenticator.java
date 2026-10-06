package com.jobproof.shared.auth;

import java.util.Optional;

/** Resolves a session cookie to the signed-in account. Implemented by the identity module. */
public interface SessionAuthenticator {

    Optional<CurrentAccount> authenticate(String rawToken);
}
