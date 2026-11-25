package com.example.panel.security;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Check the password cost before account state, including disabled accounts. Unknown users use Dao's dummy hash. */
final class UniformAuthenticationProvider extends DaoAuthenticationProvider {
    UniformAuthenticationProvider(UserDetailsService users, PasswordEncoder passwords) {
        setUserDetailsService(users);
        setPasswordEncoder(passwords);
        setHideUserNotFoundExceptions(true);
        setPreAuthenticationChecks(user -> {});
        setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) throw new DisabledException("Account unavailable");
            if (!user.isAccountNonLocked()) throw new LockedException("Account unavailable");
            if (!user.isAccountNonExpired()) throw new AccountExpiredException("Account unavailable");
            if (!user.isCredentialsNonExpired()) throw new CredentialsExpiredException("Account unavailable");
        });
    }
}
