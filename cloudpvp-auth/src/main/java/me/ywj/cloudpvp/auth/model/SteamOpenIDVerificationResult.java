package me.ywj.cloudpvp.auth.model;

/**
 * SteamOpenIDVerificationResult
 * Steam OpenID 验证结果
 *
 * @author sheip9
 * @since 2026/4/15
 **/
public class SteamOpenIDVerificationResult {
    private final boolean valid;
    private final Long steamId64;

    private SteamOpenIDVerificationResult(boolean valid, Long steamId64) {
        this.valid = valid;
        this.steamId64 = steamId64;
    }

    public boolean isValid() {
        return valid;
    }

    public Long getSteamId64() {
        return steamId64;
    }

    /**
     * 创建验证失败的结果
     */
    public static SteamOpenIDVerificationResult invalid() {
        return new SteamOpenIDVerificationResult(false, null);
    }

    /**
     * 创建验证成功的结果
     *
     * @param steamId64 Steam ID
     */
    public static SteamOpenIDVerificationResult success(Long steamId64) {
        return new SteamOpenIDVerificationResult(true, steamId64);
    }
}
