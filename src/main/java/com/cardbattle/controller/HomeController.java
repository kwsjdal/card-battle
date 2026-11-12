package com.cardbattle.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后端根路径引导页。
 * 本项目前后端分离: 8080 只是 REST 接口, 没有任何页面;
 * 以前直接访问 localhost:8080 会看到 Whitelabel 404 白板页, 容易让人以为服务挂了,
 * 现在返回一个引导页, 说明这是后端并自动跳转到前端 5173。
 */
@RestController
public class HomeController {

    @GetMapping(value = {"/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"utf-8\">"
                + "<meta http-equiv=\"refresh\" content=\"3;url=http://localhost:5173\">"
                + "<title>卡牌对战系统 · 后端服务</title>"
                + "<style>"
                + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:linear-gradient(160deg,#14171c,#0a0c10);font-family:'Microsoft YaHei',sans-serif;color:#e8dcc4}"
                + ".card{max-width:560px;padding:48px 56px;background:rgba(30,35,41,.9);border:1px solid #3a424d;"
                + "border-left:3px solid #d4a847;border-radius:4px;box-shadow:0 12px 48px rgba(0,0,0,.6)}"
                + "h1{margin:0 0 6px;font-size:26px;letter-spacing:6px;color:#f0cc6d}"
                + "p{margin:10px 0;font-size:14px;line-height:1.9;color:#9aa3ad}"
                + "code{background:#0a0c10;border:1px solid #2a313a;border-radius:3px;padding:2px 8px;color:#7fb8e6;font-size:13px}"
                + "a.btn{display:inline-block;margin-top:18px;padding:12px 34px;border-radius:3px;text-decoration:none;"
                + "background:linear-gradient(180deg,#f2ad36,#b8781a);color:#1a1005;font-weight:700;letter-spacing:3px}"
                + ".tip{font-size:12px;color:#5f6a75;margin-top:22px}"
                + "</style></head><body><div class=\"card\">"
                + "<h1>卡牌对战系统 · 后端服务</h1>"
                + "<p>这里是 <code>localhost:8080</code> —— 纯 REST 接口服务（Spring Boot），没有页面，所以刚才你看到的是 404。</p>"
                + "<p>游戏页面在前端：<code>localhost:5173</code>，3 秒后自动跳转……</p>"
                + "<a class=\"btn\" href=\"http://localhost:5173\">进入游戏 →</a>"
                + "<p class=\"tip\">接口统一前缀 <code>/api/*</code>，需携带登录后发放的 JWT；<br>"
                + "本文档项目演示环境：前端 Vue3 + Vite（5173） · 后端 Spring Boot（8080） · MySQL + Redis</p>"
                + "</div></body></html>";
    }
}
