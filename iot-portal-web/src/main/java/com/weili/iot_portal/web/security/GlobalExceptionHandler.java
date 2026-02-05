package com.weili.iot_portal.web.security;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.weili.basic.common.exception.BaseException;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import static com.weili.basic.common.enums.ErrorCodeConstants.*;

/**
 * 全局异常处理器，将 Exception 翻译成 CommonResult + 对应的异常编号
 */
@RestControllerAdvice
@AllArgsConstructor
@Slf4j
@Component
public class GlobalExceptionHandler {

    /**
     * 处理 SpringMVC 请求参数缺失
     * <p>
     * 例如说，接口上设置了 @RequestParam("xx") 参数，结果并未传递 xx 参数
     */
    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public CommonResult<?> missingServletRequestParameterExceptionHandler(MissingServletRequestParameterException ex) {
        log.warn("[missingServletRequestParameterExceptionHandler]", ex);
        return CommonResult.error(BAD_REQUEST.getCode(), String.format("请求参数缺失:%s", ex.getParameterName()));
    }

    /**
     * 处理 SpringMVC 请求参数类型错误
     * <p>
     * 例如说，接口上设置了 @RequestParam("xx") 参数为 Integer，结果传递 xx 参数类型为 String
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public CommonResult<?> methodArgumentTypeMismatchExceptionHandler(MethodArgumentTypeMismatchException ex) {
        log.warn("[methodArgumentTypeMismatchExceptionHandler]", ex);
        return CommonResult.error(BAD_REQUEST.getCode(), String.format("请求参数类型错误:%s", ex.getMessage()));
    }

    /**
     * 处理 SpringMVC 参数校验不正确
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<?> methodArgumentNotValidExceptionExceptionHandler(MethodArgumentNotValidException ex) {
        log.warn("[methodArgumentNotValidExceptionExceptionHandler]", ex);
        FieldError fieldError = ex.getBindingResult().getFieldError();
        return CommonResult.error(BAD_REQUEST.getCode(), String.format("请求参数不正确:%s", fieldError.getDefaultMessage()));
    }

    /**
     * 处理 SpringMVC 参数绑定不正确，本质上也是通过 Validator 校验
     */
    @ExceptionHandler(BindException.class)
    public CommonResult<?> bindExceptionHandler(BindException ex) {
        log.warn("[handleBindException]", ex);
        FieldError fieldError = ex.getFieldError();
        return CommonResult.error(BAD_REQUEST.getCode(), String.format("请求参数不正确:%s", fieldError.getDefaultMessage()));
    }

    /**
     * 处理 SpringMVC 请求参数类型错误
     * <p>
     * 例如说，接口上设置了 @RequestBody实体中 xx 属性类型为 Integer，结果传递 xx 参数类型为 String
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public CommonResult<?> methodArgumentTypeInvalidFormatExceptionHandler(HttpMessageNotReadableException ex) {
        log.warn("[methodArgumentTypeInvalidFormatExceptionHandler]{}", ex.toString());
        if (ex.getCause() instanceof InvalidFormatException invalidFormatException) {
            return CommonResult.error(BAD_REQUEST.getCode(), String.format("请求参数类型错误:%s", invalidFormatException.getValue()));
        } else {
            return defaultExceptionHandler(ex);
        }
    }

    /**
     * 处理 Validator 校验不通过产生的异常
     */
    @ExceptionHandler(value = ConstraintViolationException.class)
    public CommonResult<?> constraintViolationExceptionHandler(ConstraintViolationException ex) {
        log.warn("[constraintViolationExceptionHandler]", ex);
        ConstraintViolation<?> constraintViolation = ex.getConstraintViolations().iterator().next();
        return CommonResult.error(BAD_REQUEST.getCode(), String.format("请求参数不正确:%s", constraintViolation.getMessage()));
    }

    /**
     * 处理 SpringMVC 请求地址不存在
     * <p>
     * 注意，它需要设置如下两个配置项：
     * 1. spring.mvc.throw-exception-if-no-handler-found 为 true
     * 2. spring.mvc.static-path-pattern 为 /statics/**
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public CommonResult<?> noHandlerFoundExceptionHandler(NoHandlerFoundException ex) {
        log.warn("[noHandlerFoundExceptionHandler]", ex);
        return CommonResult.error(NOT_FOUND.getCode(), String.format("请求地址不存在:%s", ex.getRequestURL()));
    }

    /**
     * 处理 SpringMVC 请求方法不正确
     * <p>
     * 例如说，A 接口的方法为 GET 方式，结果请求方法为 POST 方式，导致不匹配
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public CommonResult<?> httpRequestMethodNotSupportedExceptionHandler(HttpRequestMethodNotSupportedException ex) {
        log.warn("[httpRequestMethodNotSupportedExceptionHandler]", ex);
        return CommonResult.error(METHOD_NOT_ALLOWED.getCode(), String.format("请求方法不正确:%s", ex.getMessage()));
    }

    /**
     * 处理 Spring Security 权限不足的异常
     * <p>
     * 来源是，使用 @PreAuthorize 注解，AOP 进行权限拦截
     */
    @ExceptionHandler(value = AccessDeniedException.class)
    public CommonResult<?> accessDeniedExceptionHandler(HttpServletRequest req, AccessDeniedException ex) {
        log.warn("[accessDeniedExceptionHandler][ 无法访问 url({})]", req.getRequestURL(), ex);
        return CommonResult.error(FORBIDDEN.getCode(), FORBIDDEN.getMsg());
    }

    /**
     * 处理业务异常 ServiceException
     */
    @ExceptionHandler(value = ServiceException.class)
    public CommonResult<?> serviceExceptionHandler(ServiceException ex) {
        return CommonResult.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理业务异常 BaseException
     */
    @ExceptionHandler(value = BaseException.class)
    public CommonResult<?> baseExceptionHandler(BaseException ex) {
        return CommonResult.error(INTERNAL_SERVER_ERROR.getCode(), ex.getMessage());
    }

    /**
     * 处理响应写入异常（如 Content-Type 不匹配、响应已提交等）
     * <p>
     * 这通常发生在客户端断开连接后，响应已经部分写入，无法再写入新的响应体
     * </p>
     */
    @ExceptionHandler(value = org.springframework.http.converter.HttpMessageNotWritableException.class)
    public void httpMessageNotWritableExceptionHandler(org.springframework.http.converter.HttpMessageNotWritableException ex) {
        // 检查是否是客户端断开连接导致的
        if (isClientAbortException(ex) || isResponseCommitted()) {
            // 客户端已断开或响应已提交，静默处理
            if (log.isDebugEnabled()) {
                log.debug("[GlobalExceptionHandler] 响应写入失败（客户端断开或响应已提交），跳过处理: {}", ex.getMessage());
            }
            // 不返回任何值，让 Spring 跳过响应写入
            return;
        }
        // 其他原因导致的写入失败，记录警告
        log.warn("[GlobalExceptionHandler] 响应写入失败: {}", ex.getMessage());
    }

    /**
     * 处理系统异常，兜底处理所有的一切
     * <p>
     * 优化：检查客户端断开连接异常，如果是客户端断开，静默处理（不记录错误日志）
     * 这是正常情况，不是错误，符合 webhook "fire and forget" 模式的最佳实践
     * </p>
     */
    @ExceptionHandler(value = Exception.class)
    public CommonResult<?> defaultExceptionHandler(Throwable ex) {
        // 优先检查响应是否已提交（防止在响应已写入后尝试返回 CommonResult）
        if (isResponseCommitted()) {
            // 响应已提交，无法写入新的响应体
            // 这通常发生在客户端断开连接后，响应已经部分写入
            if (isClientAbortException(ex)) {
                // 客户端断开，静默处理
                if (log.isDebugEnabled()) {
                    log.debug("[GlobalExceptionHandler] 客户端已断开连接且响应已提交，跳过异常处理: {}", ex.getClass().getSimpleName());
                }
            } else {
                // 其他异常但响应已提交，记录警告
                log.warn("[GlobalExceptionHandler] 响应已提交，无法返回错误响应: {}", ex.getClass().getSimpleName());
            }
            // 返回 null，Spring 会跳过响应写入
            return null;
        }
        
        // 检查是否是客户端断开连接异常（响应未提交的情况）
        if (isClientAbortException(ex)) {
            // 客户端已断开，静默处理（DEBUG 级别日志）
            // 这是正常情况，不是错误，不应记录 ERROR 日志
            // 符合行业最佳实践：GitHub、Stripe、AWS 等 webhook 服务都采用这种方式
            if (log.isDebugEnabled()) {
                log.debug("[GlobalExceptionHandler] 客户端已断开连接，跳过异常处理: {}", ex.getMessage());
            }
            // 返回 null，让 Spring 跳过响应写入
            return null;
        }
        
        // 其他异常正常处理
        log.error("[defaultExceptionHandler]", ex);
        return CommonResult.error(INTERNAL_SERVER_ERROR.getCode(), INTERNAL_SERVER_ERROR.getMsg());
    }

    /**
     * 检查是否是客户端断开连接异常
     * <p>
     * 客户端断开连接是正常情况，不是错误：
     * - 客户端可能设置了较短的超时时间
     * - 服务器已立即返回响应，异步处理继续执行
     * - 这是 webhook "fire and forget" 模式的标准行为
     * </p>
     *
     * @param ex 异常对象
     * @return true 如果是客户端断开连接异常
     */
    private boolean isClientAbortException(Throwable ex) {
        if (ex == null) {
            return false;
        }
        
        // 检查异常类型
        if (ex instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException) {
            return true;
        }
        
        // 检查异常消息
        String message = ex.getMessage();
        if (message != null && (
                message.contains("Broken pipe")
                || message.contains("ClientAbortException")
                || message.contains("Connection reset by peer")
                || message.contains("java.io.IOException: Broken pipe")
        )) {
            return true;
        }
        
        // 检查 cause
        Throwable cause = ex.getCause();
        if (cause != null) {
            if (cause instanceof org.apache.catalina.connector.ClientAbortException) {
                return true;
            }
            if (cause instanceof java.io.IOException 
                    && cause.getMessage() != null 
                    && (cause.getMessage().contains("Broken pipe")
                        || cause.getMessage().contains("Connection reset"))) {
                return true;
            }
            // 递归检查嵌套的 cause
            if (isClientAbortException(cause)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 检查响应是否已提交
     * <p>
     * 如果响应已提交，则无法再写入响应体，尝试写入会导致异常
     * </p>
     *
     * @return true 如果响应已提交
     */
    private boolean isResponseCommitted() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletResponse response = attributes.getResponse();
                if (response != null) {
                    return response.isCommitted();
                }
            }
        } catch (Exception e) {
            // 如果无法获取响应对象，假设响应未提交
            // 这通常发生在异常处理过程中，响应对象可能已经失效
            if (log.isDebugEnabled()) {
                log.debug("[GlobalExceptionHandler] 无法检查响应状态: {}", e.getMessage());
            }
        }
        return false;
    }

}
