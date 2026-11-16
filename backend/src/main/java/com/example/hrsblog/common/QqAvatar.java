package com.example.hrsblog.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ 邮箱头像派生：把 {@code 数字@qq.com} 形式的邮箱映射成 QQ 公开头像地址。
 *
 * <p>规则（前端 {@code api/guest-messages.js} 的 {@code deriveAvatarUrl} 必须与本实现保持一致）：
 * <ul>
 *   <li>邮箱用户名为纯数字、域名为 {@code qq.com}（不区分大小写）时才派生头像；</li>
 *   <li>其余邮箱一律返回 {@code null}，由前端回退默认头像；</li>
 *   <li>派生结果只包含 QQ 号本身，不包含邮箱原文。</li>
 * </ul>
 *
 * <p>头像地址来自腾讯的公开接口，{@code <img>} 标签加载图片不受 CORS 限制，
 * 因此不需要后端代理，也不需要在任何地方保存派生结果。
 */
public final class QqAvatar {

    /** 头像尺寸参数：100px 足够覆盖前台的 48px 圆形头像。 */
    private static final String SPEC = "100";

    private static final Pattern QQ_EMAIL = Pattern.compile("^(\\d+)@qq\\.com$", Pattern.CASE_INSENSITIVE);

    private QqAvatar() {
    }

    /**
     * 由邮箱派生 QQ 头像地址。
     *
     * @param email 留言者填写的邮箱，可为 null
     * @return 头像地址；不是 QQ 邮箱时返回 {@code null}
     */
    public static String derive(String email) {
        if (email == null) {
            return null;
        }
        Matcher matcher = QQ_EMAIL.matcher(email.trim());
        if (!matcher.matches()) {
            return null;
        }
        return "https://q1.qlogo.cn/headimg_dl?dst_uin=" + matcher.group(1) + "&spec=" + SPEC;
    }
}
