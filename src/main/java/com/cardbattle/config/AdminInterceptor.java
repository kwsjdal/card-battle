package com.cardbattle.config;

import com.cardbattle.entity.User;
import com.cardbattle.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 管理员拦截器: 在 JwtInterceptor 之后执行, 校验 role=admin */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        Long userId = (Long) req.getAttribute("userId");
        User u = userId == null ? null : userMapper.selectById(userId);
        if (u != null && "admin".equals(u.getRole())) return true;
        resp.setStatus(403);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":1,\"msg\":\"无管理员权限\"}");
        return false;
    }
}
