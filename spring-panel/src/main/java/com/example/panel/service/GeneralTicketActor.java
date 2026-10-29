package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import java.sql.Types;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Immutable account snapshot. Rights and account status are checked by each owning SQL statement. */
record GeneralTicketActor(long userId,String username) {
    static GeneralTicketActor from(Authentication authentication) {
        if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken
                ||!(authentication.getPrincipal() instanceof PanelUserPrincipal account)
                ||account.getUserId()<=0||account.getUsername()==null||account.getUsername().isBlank()
                ||!account.isEnabled()||!account.isAccountNonLocked()||!account.isAccountNonExpired()||!account.isCredentialsNonExpired())return null;
        return new GeneralTicketActor(account.getUserId(),account.getUsername());
    }
    /** Internal persisted command identity only; never resolves or authenticates a user by name. */
    static GeneralTicketActor stored(long userId,String username) {return new GeneralTicketActor(userId,username);}
    boolean valid(){return userId>0&&username!=null&&!username.isBlank();}
    MapSqlParameterSource bind(MapSqlParameterSource parameters){
        return parameters.addValue("authenticatedActorId",userId,Types.BIGINT).addValue("identity",username,Types.VARCHAR);
    }
    @Override public String toString(){return "GeneralTicketActor";}
}
