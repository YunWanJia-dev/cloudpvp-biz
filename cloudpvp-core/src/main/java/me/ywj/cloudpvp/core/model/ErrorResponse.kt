package me.ywj.cloudpvp.core.model

/**
 * ErrorReturn
 *
 * @author sheip9
 * @since 2024/10/18 23:27
 */
data class ErrorResponse(val errors: ErrorContent) {
    constructor(id: ErrorType, detail: String) : this(ErrorContent(id, detail))
}

data class ErrorContent(val id: ErrorType, val detail: String)

enum class ErrorType {
    PLAYER_ID_INVALID
}