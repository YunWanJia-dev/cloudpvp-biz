package me.ywj.cloudpvp.core.entity

import me.ywj.cloudpvp.core.type.SteamID64
import java.util.*

/**
 * PlayerProfile
 * 玩家资料基础模型。
 *
 * @author sheip9
 * @since 2024/10/16 11:49
 */
open class PlayerProfile(
    open val steamID64: SteamID64,
    open val name: String,
    open val avatarLink: String,
) {
    // 缓存实体需要继承该模型并覆盖审计时间，基础模型保持框架无关。
    open var auditAt: Date? = null
}
