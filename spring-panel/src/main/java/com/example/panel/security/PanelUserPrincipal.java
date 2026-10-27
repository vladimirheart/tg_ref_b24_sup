package com.example.panel.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Stable account identity retained when a security context is serialized by Spring Session. */
public final class PanelUserPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final long userId;

    public PanelUserPrincipal(long userId, String username, String password, boolean enabled,
                       Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.userId = userId;
    }

    public long getUserId() { return userId; }
}
