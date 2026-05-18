package me.ywj.cloudpvp.play.controller;

import me.ywj.cloudpvp.core.model.play.Game;
import me.ywj.cloudpvp.core.model.play.Type;
import me.ywj.cloudpvp.play.service.GameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GameController
 * 游戏和模式列表控制器
 *
 * @author sheip9
 * @since 2024/9/1 12:00
 */
@RestController
@RequestMapping("/games")
public class GameController {
    private final GameService gameService;

    /**
     * 创建游戏和模式列表控制器。
     *
     * @param gameService 游戏和模式查询服务
     */
    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public List<Game> getGames() {
        return gameService.getGames();
    }

    @GetMapping("/{key}/modes")
    public List<Type> getModes(@PathVariable String key) {
        return gameService.getModes(key);
    }
}
