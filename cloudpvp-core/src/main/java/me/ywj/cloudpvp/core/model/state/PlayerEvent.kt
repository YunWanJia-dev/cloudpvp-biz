package me.ywj.cloudpvp.core.model.state

import lombok.Builder

/**
 * PlayerEvent
 *
 * @author sheip9
 * @since 2024/10/19 22:14
 */
@Builder
data class PlayerEvent(val action: PlayerEventAction, val value: Any?)

enum class PlayerEventAction