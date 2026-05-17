package me.ywj.cloudpvp.core.model.steam;

import com.fasterxml.jackson.core.type.TypeReference;
import me.ywj.cloudpvp.core.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PlayerSummaryTest
 * Steam 玩家摘要响应测试。
 *
 * @author sheip9
 * @since 2026/5/15 19:23
 */
class PlayerSummaryTest {
    /**
     * 验证 Steam 返回的数字枚举值可以映射为本地枚举。
     *
     * @throws Exception JSON 解析异常
     */
    @Test
    void steamNumericEnumValuesCanDeserialize() throws Exception {
        String json = """
                {
                  "response": {
                    "players": [
                      {
                        "steamid": "76561198842604564",
                        "communityvisibilitystate": 3,
                        "profilestate": 1,
                        "personaname": "云玩家战队的sheip9本人",
                        "profileurl": "https://steamcommunity.com/id/sheip9/",
                        "avatar": "https://avatars.steamstatic.com/avatar.jpg",
                        "avatarmedium": "https://avatars.steamstatic.com/avatar_medium.jpg",
                        "avatarfull": "https://avatars.steamstatic.com/avatar_full.jpg",
                        "avatarhash": "1b078dfe252763aafa26707ab1c986889efa4929",
                        "lastlogoff": 1715700000,
                        "personastate": 1,
                        "personastateflags": 0,
                        "primaryclanid": "103582791429521408",
                        "timecreated": 1530000000
                      }
                    ]
                  }
                }
                """;

        SteamResponse<GetPlayerSummariesResponse> response = JacksonUtils.deserialize(
                json,
                new TypeReference<>() {
                }
        );
        PlayerSummary summary = response.getResponse().getPlayers().get(0);

        assertEquals(76561198842604564L, summary.getSteamId());
        assertEquals(CommunityVisibilityStateEnum.PUBLIC, summary.getCommunityVisibilityState());
        assertEquals(PersonaStateEnum.ONLINE, summary.getPersonaState());
    }
}
