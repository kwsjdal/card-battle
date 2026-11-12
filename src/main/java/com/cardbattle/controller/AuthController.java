package com.cardbattle.controller;

import com.cardbattle.common.Result;
import com.cardbattle.dto.LoginDTO;
import com.cardbattle.dto.RegisterDTO;
import com.cardbattle.dto.ResetDTO;
import com.cardbattle.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final com.cardbattle.service.SessionService sessionService;

    /** 主动退出登录: 清除本账号的会话标记(换设备登录时不会被提示"被挤下线") */
    @PostMapping("/logout")
    public Result<Void> logout(javax.servlet.http.HttpServletRequest req) {
        Object uid = req.getAttribute("userId");
        if (uid != null) sessionService.logout(Long.valueOf(String.valueOf(uid)));
        return Result.ok();
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody @Validated RegisterDTO dto) {
        return Result.ok(userService.register(dto));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Validated LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    /** 手机号 + 短信验证码免密登录 */
    @PostMapping("/login-by-code")
    public Result<Map<String, Object>> loginByCode(@RequestBody Map<String, String> body) {
        return Result.ok(userService.loginByCode(body.get("phone"), body.get("code")));
    }

    /** 忘记密码: 手机号 + 短信验证码 → 重置密码 */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody @Validated ResetDTO dto) {
        userService.resetPassword(dto.getPhone(), dto.getCode(), dto.getNewPassword());
        return Result.ok();
    }
}
