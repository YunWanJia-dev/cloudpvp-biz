package me.ywj.cloudpvp.state.constant

/**
 * StateEnum
 *
 * @author sheip9
 * @since 2024/10/16 16:42
 */
enum class StateEnum(i: Byte) {
    OFFLINE(-1),
    UNKNOWN(0),
    ONLINE(1),
    FINDING_MATCH(2),
    IN_MATCH(3),
    ;
}