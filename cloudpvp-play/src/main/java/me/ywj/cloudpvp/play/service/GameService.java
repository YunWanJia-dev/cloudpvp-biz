package me.ywj.cloudpvp.play.service;

import lombok.AllArgsConstructor;
import me.ywj.cloudpvp.beans.property.PlayProperty;
import me.ywj.cloudpvp.core.model.play.Game;
import me.ywj.cloudpvp.core.model.play.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * GameService
 * 从配置中查询游戏和模式信息。
 *
 * @author sheip9
 * @since 2026/5/18 11:56
 */
@Service
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class GameService {
    private final PlayProperty playProperty;

    /**
     * 获取已配置的游戏列表。
     *
     * @return 游戏列表
     */
    public List<Game> getGames() {
        return playProperty.getGames();
    }

    /**
     * 获取指定游戏已配置的模式分类列表。
     *
     * @param key 游戏唯一标识
     * @return 模式分类列表，未配置游戏时返回空列表
     */
    public List<Type> getModes(String key) {
        // 游戏和模式由配置中心维护，避免代码内静态池与运行配置分叉。
        return playProperty.getGames().stream()
                .filter(game -> key.equals(game.getKey()))
                .findFirst()
                .map(Game::getTypes)
                .orElse(List.of());
    }
}
