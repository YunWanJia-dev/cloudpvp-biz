package me.ywj.cloudpvp.play.constant;

import me.ywj.cloudpvp.core.model.play.Game;

import java.util.List;


/**
 * GamePool
 * 游戏池
 * 
 * @author sheip9 
 * @since 2024/9/1 12:00
 */
public class GamePool {
    static final Game CSGO = new Game("CSGO", "CS:GO", "");
    static final Game CS2 = new Game("CS2", "CS2", "");
    /**
     * GameList
     * <br> 
     * 游戏列表
     * 因为变动少所以不存数据库先
     */
    public static final List<Game> GAME_LIST = List.of(CS2, CSGO);
}
