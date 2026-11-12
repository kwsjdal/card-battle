package com.cardbattle.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 登录拦截器: 校验 Authorization: Bearer <token>, 解析出的userId放入request属性 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final AuthSupport authSupport;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        String auth = req.getHeader("Authorization");
        String token = (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
        AuthSupport.AuthResult r = new AuthSupport.AuthResult();
        Long userId = authSupport.authenticate(token, r);
        if (userId == null) {
            resp.setStatus(r.msg.contains("封禁") ? 403 : 401);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":1,\"msg\":\"" + r.msg + "\"}");
            return false;
        }
        req.setAttribute("userId", userId);
        return true;
    }
}
