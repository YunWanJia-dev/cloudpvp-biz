package me.ywj.cloudpvp.beans.component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.ywj.cloudpvp.beans.ModuleInfo;
import me.ywj.cloudpvp.core.exception.BizException;
import me.ywj.cloudpvp.core.utils.ExceptionMessageUtils;
import me.ywj.cloudpvp.core.utils.FeishuWebhookUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.net.URI;

@RestControllerAdvice
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class ExceptionAdvice {
    private final FeishuWebhookUtils feishuWebhookUtils;
    private final ModuleInfo moduleInfo;

    @ExceptionHandler(BizException.class)
    public ProblemDetail handleBizException(BizException e, HttpServletRequest request, HttpServletResponse response) {
        HttpStatus status = getHttpStatus(e);
        response.setStatus(status.value());
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        body.setTitle(status.getReasonPhrase());
        body.setInstance(URI.create(request.getRequestURI()));
        return body;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail exception(Exception e, HttpServletRequest request, HttpServletResponse response) {
        Thread.ofVirtual().start(() -> feishuWebhookUtils.send(ExceptionMessageUtils.unexpectedExceptionMessage(moduleInfo.getName(), e)));
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        response.setStatus(status.value());
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        body.setTitle(status.getReasonPhrase());
        body.setInstance(URI.create(request.getRequestURI()));
        return body;
    }

    private HttpStatus getHttpStatus(BizException e) {
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        if (responseStatus != null) {
            return responseStatus.code();
        }
        return HttpStatus.BAD_REQUEST;
    }
}
