package me.ywj.cloudpvp.core.model.configurations;

import lombok.Data;

@Data
public class JWTConfiguration {
    private String secret;
    private long expireTime;
}
