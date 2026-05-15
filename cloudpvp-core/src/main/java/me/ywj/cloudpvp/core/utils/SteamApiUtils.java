package me.ywj.cloudpvp.core.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import me.ywj.cloudpvp.core.constant.steam.SteamApiUrl;
import me.ywj.cloudpvp.core.model.configurations.SteamApiConfiguration;
import me.ywj.cloudpvp.core.model.steam.GetPlayerSummariesResponse;
import me.ywj.cloudpvp.core.model.steam.SteamResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SteamApiUtils
 * Steam Web API 请求工具类。
 *
 * @author sheip9
 * @since 2026/5/15 15:41
 */
public class SteamApiUtils {
    private final SteamApiConfiguration configuration;
    private final HttpUtils httpUtils;

    /**
     * 创建 Steam Web API 请求工具。
     *
     * @param configuration Steam API 配置
     */
    public SteamApiUtils(SteamApiConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        // Steam Web API 后续接口共享同一个根地址，具体 endpoint 留在各自方法里，避免类级常量堆积。
        this.httpUtils = new HttpUtils(
                HttpRequest.newBuilder()
                        .uri(URI.create(SteamApiUrl.WORLD_WIDE))
                        .build()
        );
    }

    /**
     * 批量查询玩家的 Steam 资料摘要。
     *
     * @param steamIds Steam ID64 列表，最多 100 个
     * @return Steam 玩家资料摘要响应
     * @throws Exception 当请求 Steam API 或解析响应失败时抛出
     */
    public SteamResponse<GetPlayerSummariesResponse> getPlayerSummaries(Collection<Long> steamIds) throws Exception {
        // endpoint 专属参数放在方法内部，后续新增 API 时每个方法能独立阅读和维护自己的请求契约。
        final int maxSteamIds = 100;
        final String path = "ISteamUser/GetPlayerSummaries/v0002/";
        final TypeReference<SteamResponse<GetPlayerSummariesResponse>> responseType =
                new TypeReference<>() {
                };

        // key 是 Steam API 的必填参数，提前失败比发出无效请求后再排查远端响应更直接。
        if (configuration.getKey() == null || configuration.getKey().isBlank()) {
            throw new IllegalArgumentException("Steam API key must not be empty");
        }

        // Steam 官方限制该接口最多一次查 100 个 ID，本地拦截可以避免无意义的外部请求。
        if (steamIds == null || steamIds.isEmpty()) {
            throw new IllegalArgumentException("steamIds must not be empty");
        }
        if (steamIds.size() > maxSteamIds) {
            throw new IllegalArgumentException("steamIds must not contain more than 100 IDs");
        }

        // SteamID64 的有效性规则集中在 PlayerUtils，避免 Steam API 工具类复制一份业务判断。
        if (steamIds.stream().anyMatch(steamId -> !PlayerUtils.INSTANCE.checkIdIsValid(steamId))) {
            throw new IllegalArgumentException("steamIds must contain valid Steam ID64 values");
        }

        // Steam 接口要求多个 ID 作为一个逗号分隔的 steamids 参数，而不是重复 query 参数。
        String steamIdsValue = steamIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        var response = httpUtils.get(path, List.of(
                Map.entry("key", configuration.getKey()),
                Map.entry("steamids", steamIdsValue)
        ));

        // JDK HttpClient 不会因为非 2xx 状态码自动抛异常，这里显式阻止错误页面进入 JSON 解析。
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Steam API request failed with HTTP status: " + response.statusCode());
        }

        // Steam 响应外层是泛型包装，显式 TypeReference 才能保留内部 response 的目标类型。
        return JacksonUtils.deserialize(response.body(), responseType);
    }
}
