package com.example.panel.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Stable account identity retained when a security context is serialized by Spring Session. */
public final class PanelUserPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final long userId;
    private final long sessionVersion;

    public PanelUserPrincipal(long userId, String username, String password, boolean enabled,
                       Collection<? extends GrantedAuthority> authorities) {
        this(userId, username, password, enabled, authorities, 0L);
    }

    public PanelUserPrincipal(long userId, String username, String password, boolean enabled,
                              Collection<? extends GrantedAuthority> authorities, long sessionVersion) {
        super(username, password, enabled, true, true, true, authorities);
        this.userId = userId;
        this.sessionVersion = sessionVersion;
    }

    public long getUserId() { return userId; }
    public long getSessionVersion() { return sessionVersion; }
}
