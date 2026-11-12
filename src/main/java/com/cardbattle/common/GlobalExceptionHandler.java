package com.cardbattle.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> biz(BizException e) {
        return Result.fail(e.getMessage());
    }

    /** 参数校验失败: 返回具体字段提示 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> valid(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().isEmpty()
                ? "参数不合法"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.fail(msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> other(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统繁忙, 请稍后再试");
    }
}
