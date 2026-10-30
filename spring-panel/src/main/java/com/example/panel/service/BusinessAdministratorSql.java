package com.example.panel.service;

import com.example.panel.security.PanelRolePermissionSnapshot;
import com.example.panel.security.PanelUserPrincipal;
import java.sql.Types;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Every administration statement proves its original account and current system privileges. */
final class BusinessAdministratorSql {
    // Uses the same assigned-role interpretation as login and GENERAL administration.
    private static final String ADMINISTRATOR = """
        WITH account_actor AS MATERIALIZED (
            SELECT account.id,account.role_id FROM users account
            WHERE account.id=:authenticatedActorId AND account.username=:identity
              AND account.enabled=TRUE AND COALESCE(account.is_blocked,FALSE)=FALSE
              AND 1=(SELECT COUNT(*) FROM users duplicate WHERE lower(duplicate.username)=lower(account.username))
            FOR SHARE OF account
        ), assigned_role AS MATERIALIZED (
            SELECT assigned.id FROM roles assigned JOIN account_actor actor ON actor.role_id=assigned.id
            WHERE assigned.id=:assignedRoleId AND assigned.name IS NOT DISTINCT FROM :assignedRoleName
              AND assigned.permissions IS NOT DISTINCT FROM :assignedPermissions
            FOR SHARE OF assigned
        ), direct_admin AS MATERIALIZED (
            SELECT authority.user_id FROM user_authorities authority JOIN account_actor actor ON actor.id=authority.user_id
            WHERE authority.authority='ROLE_ADMIN' FOR SHARE OF authority
        ), direct_settings AS MATERIALIZED (
            SELECT authority.user_id FROM user_authorities authority JOIN account_actor actor ON actor.id=authority.user_id
            WHERE authority.authority='PAGE_SETTINGS' FOR SHARE OF authority
        ), administrator AS MATERIALIZED (
            SELECT actor.id FROM account_actor actor
            WHERE (EXISTS(SELECT 1 FROM direct_admin) OR (:assignedAdmin AND EXISTS(SELECT 1 FROM assigned_role)))
              AND (EXISTS(SELECT 1 FROM direct_settings) OR (:assignedSettings AND EXISTS(SELECT 1 FROM assigned_role)))
        )
        """;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate sql;
    BusinessAdministratorSql(JdbcTemplate jdbc){this.jdbc=jdbc;this.sql=new NamedParameterJdbcTemplate(jdbc);}

    <T> List<T> read(Authentication authentication,String query,RowMapper<T> mapper,Map<String,?> values){
        return execute(authentication,"SELECT TRUE AS __present, operation.* FROM administrator CROSS JOIN LATERAL ("+query+") operation",mapper,values);
    }
    /** The supplied DML must select from, or join, administrator; no mutation precedes that proof. */
    <T> List<T> change(Authentication authentication,String statement,RowMapper<T> mapper,Map<String,?> values){
        return execute(authentication,statement,mapper,values);
    }
    boolean change(Authentication authentication,String statement,Map<String,?> values){
        return !change(authentication,statement+" RETURNING TRUE AS __present",(rs,row)->true,values).isEmpty();
    }
    private <T> List<T> execute(Authentication authentication,String statement,RowMapper<T> mapper,Map<String,?> values){
        if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken
                ||!(authentication.getPrincipal() instanceof PanelUserPrincipal account)||account.getUserId()<=0
                ||account.getUsername().isBlank()||!account.isEnabled()||!account.isAccountNonLocked()
                ||!account.isAccountNonExpired()||!account.isCredentialsNonExpired())throw denied();
        var parameters=new MapSqlParameterSource().addValues(values)
            .addValue("authenticatedActorId",account.getUserId(),Types.BIGINT).addValue("identity",account.getUsername(),Types.VARCHAR);
        PanelRolePermissionSnapshot.capture(jdbc,account.getUserId(),account.getUsername()).bind(parameters);
        return sql.query(ADMINISTRATOR+", result AS MATERIALIZED ("+statement+") "+"""
            SELECT EXISTS(SELECT 1 FROM administrator) AS __authorized,result.*
            FROM (VALUES(TRUE)) request(active) LEFT JOIN result ON TRUE
            """,parameters,rs->{
                var rows=new ArrayList<T>();
                while(rs.next()){
                    if(!rs.getBoolean("__authorized"))throw denied();
                    if(rs.getBoolean("__present"))rows.add(mapper.mapRow(rs,rows.size()));
                }
                return rows;
            });
    }
    private AccessDeniedException denied(){return new AccessDeniedException("Требуются действующие ROLE_ADMIN и PAGE_SETTINGS");}
}
