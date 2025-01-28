package me.ywj.cloudpvp.beans.property

import me.ywj.cloudpvp.core.model.play.Game
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * PlayProperty
 * 游戏模式配置类
 *
 * Example：
 * ```
 * cloudpvp:
 *   play:
 *     games:
 *       - key: GAME_KEY_HERE
 *         name: GAME_NAME_HERE
 *         description: GAME_DESCRIPTION_HERE
 *         types:
 *           - key: TYPE_KEY_HERE
 *           - name: TYPE_NAME_HERE
 *           - modes:
 *               - key: MODE_KEY_HERE
 *               - name: MODE_NAME_HERE
 *               - description: MODE_DESCRIPTION_HERE
 *  ```
 */
@Configuration
@ConfigurationProperties(prefix = "cloudpvp.play")
open class PlayProperty {
    var games: MutableList<Game> = mutableListOf()
}