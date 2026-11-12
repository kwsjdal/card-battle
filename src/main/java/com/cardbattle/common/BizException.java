package com.cardbattle.common;

/** 业务异常, message直接展示给前端 */
public class BizException extends RuntimeException {
    public BizException(String msg) { super(msg); }
}
