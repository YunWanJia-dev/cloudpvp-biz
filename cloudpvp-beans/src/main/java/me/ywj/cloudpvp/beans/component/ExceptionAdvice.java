package me.ywj.cloudpvp.beans.component;

import lombok.AllArgsConstructor;
import me.ywj.cloudpvp.beans.ModuleInfo;
import me.ywj.cloudpvp.core.exception.BizException;
import me.ywj.cloudpvp.core.utils.ExceptionMessageUtils;
import me.ywj.cloudpvp.core.utils.FeishuWebhookUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class ExceptionAdvice {
    private final FeishuWebhookUtils feishuWebhookUtils;
    private final ModuleInfo moduleInfo;

    @ExceptionHandler(Exception.class)
    public String exception(Exception e) {
        if (!(e instanceof BizException)) {
            Thread.ofVirtual().start(() -> feishuWebhookUtils.send(ExceptionMessageUtils.unexpectedExceptionMessage(moduleInfo.getName(), e)));
        }
        return e.getMessage();
    }
}
