package me.ywj.cloudpvp.play.controller;

import me.ywj.cloudpvp.core.entity.play.Game;
import me.ywj.cloudpvp.play.constant.GamePool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/games")
public class GameController {
    @GetMapping
    public Game[] getGames() {
        return GamePool.GameList;
    }
}
