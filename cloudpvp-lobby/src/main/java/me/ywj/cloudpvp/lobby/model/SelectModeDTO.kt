package me.ywj.cloudpvp.lobby.model

/**
 * SelectModeDTO
 * 选择游戏模式的请求体。
 *
 * @author sheip9
 * @since 2026/5/19 14:30
 */
data class SelectModeDTO(
    val gameKey: String,
    val typeKey: String,
    val modeKey: String,
)