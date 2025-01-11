package me.ywj.cloudpvp.core.utils;

import me.ywj.cloudpvp.core.constant.header.Attributes;
import me.ywj.cloudpvp.core.entity.BasicPlayer;

import java.util.Map;

/**
 * TokenUtils
 * Token工具类
 */
public class TokenUtils {
    private JWTUtils jwtUtils;

    public TokenUtils() {
    }

    public void setJwtUtils(JWTUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    public String generateToken(Long steamId64) {
        return jwtUtils.generateToken(Map.of(Attributes.ID, steamId64));
    }

    public boolean validateToken(String token) {
        return jwtUtils.validateToken(token);
    }

    public Long getIDFromToken(String token) {
        return jwtUtils.getClaim(token, Attributes.ID);
    }
}
