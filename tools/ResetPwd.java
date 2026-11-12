import com.cardbattle.util.CryptoUtil;
import com.cardbattle.util.JwtUtil;

/** 一次性工具: 为指定账号生成 "123456" 的 盐+哈希+可逆密文 SQL (管理员重置玩家密码用) */
public class ResetPwd {
    public static void main(String[] args) throws Exception {
        long id = Long.parseLong(args[0]);
        String pwd = args.length > 1 ? args[1] : "123456";
        String salt = JwtUtil.generateSalt();
        String hash = JwtUtil.hashPassword(pwd, salt);
        String cipher = CryptoUtil.encrypt(pwd);
        System.out.println("UPDATE users SET salt='" + salt + "', password='" + hash
                + "', pwd_cipher='" + cipher + "' WHERE id=" + id + ";");
    }
}
