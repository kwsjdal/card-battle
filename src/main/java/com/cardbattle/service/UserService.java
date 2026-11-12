package com.cardbattle.service;

import com.cardbattle.dto.LoginDTO;
import com.cardbattle.dto.RegisterDTO;
import com.cardbattle.entity.User;

import java.util.Map;

public interface UserService {
    Map<String, Object> register(RegisterDTO dto);
    Map<String, Object> login(LoginDTO dto);

    /** 手机号 + 短信验证码免密登录 */
    Map<String, Object> loginByCode(String phone, String code);

    /** 手机号 + 验证码重置密码 */
    void resetPassword(String phone, String code, String newPassword);
    User getById(Long id);
}
