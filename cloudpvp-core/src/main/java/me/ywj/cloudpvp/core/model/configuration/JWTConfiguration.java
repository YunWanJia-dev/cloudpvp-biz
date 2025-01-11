package me.ywj.cloudpvp.core.model.configuration;

import lombok.Data;

@Data
public class JWTConfiguration {
    private String secret;
    private long expireTime;
}
