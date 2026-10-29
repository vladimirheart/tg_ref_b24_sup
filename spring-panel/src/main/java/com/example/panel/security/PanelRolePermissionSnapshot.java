package com.example.panel.security;

import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Assigned-role proof only. The owning SQL must recheck its raw values and lock its source row. */
public record PanelRolePermissionSnapshot(Long roleId,String roleName,String permissions,boolean adminRole,boolean settingsPage) {
    public static PanelRolePermissionSnapshot capture(JdbcTemplate jdbc,long userId,String username) {
        var rows=jdbc.query("""
            SELECT assigned.id,assigned.name,assigned.permissions FROM users account
            LEFT JOIN roles assigned ON assigned.id=account.role_id
            WHERE account.id=? AND account.username=?
            """,(rs,row)->{
                String name=rs.getString("name"),permissions=rs.getString("permissions");
                boolean admin=name!=null&&"admin".equalsIgnoreCase(name.trim());
                boolean settings=UserRepositoryUserDetailsService.mapPagePermissionsToAuthorities(
                    UserRepositoryUserDetailsService.extractPagePermissions(permissions)).contains("PAGE_SETTINGS");
                return new PanelRolePermissionSnapshot(rs.getObject("id",Long.class),name,permissions,admin,settings);
            },userId,username);
        return rows.size()==1?rows.get(0):new PanelRolePermissionSnapshot(null,null,null,false,false);
    }
    public MapSqlParameterSource bind(MapSqlParameterSource parameters) {
        return parameters.addValue("assignedRoleId",roleId,Types.BIGINT)
            .addValue("assignedRoleName",roleName,Types.VARCHAR).addValue("assignedPermissions",permissions,Types.VARCHAR)
            .addValue("assignedAdmin",adminRole,Types.BOOLEAN).addValue("assignedSettings",settingsPage,Types.BOOLEAN);
    }
    @Override public String toString(){return "PanelRolePermissionSnapshot";}
}
