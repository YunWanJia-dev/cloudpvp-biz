package me.ywj.cloudpvp.core.model.play

/**
 * Type
 *
 * @author sheip9
 * @since 2024/10/16 10:55
 */
data class Type (
    val key : String,
    val name : String,
    val modes : List<Mode>
)