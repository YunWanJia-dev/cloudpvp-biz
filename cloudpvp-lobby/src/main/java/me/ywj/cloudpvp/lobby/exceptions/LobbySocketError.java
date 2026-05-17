package me.ywj.cloudpvp.lobby.exceptions;

import me.ywj.cloudpvp.core.model.base.ErrorType;

/**
 * LobbySocketError
 * 可转换为大厅 WebSocket 错误类型的业务异常。
 *
 * @author sheip9
 * @since 2026/5/17 23:41
 */
public interface LobbySocketError {
    /**
     * 获取需要返回给 WebSocket 客户端的错误类型。
     *
     * @return WebSocket 错误类型
     */
    ErrorType getErrorType();
}
