package com.example.hrsblog.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。
 *
 * <p>接口统一返回 {@code {code, message, data}}：{@code code} 是稳定的应用级错误码，
 * HTTP 状态码由 {@link #status()} 决定，两者一一对应便于前端提示与开关处理。
 */
public enum ErrorCode {

    OK(0, HttpStatus.OK, "OK"),

    /** 参数校验失败，data 中带字段级错误。 */
    VALIDATION_FAILED(40000, HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    /** 请求体无法解析、参数类型不匹配等通用 400。 */
    BAD_REQUEST(40001, HttpStatus.BAD_REQUEST, "请求不合法"),
    /** 修改密码时原密码校验失败。 */
    UNAUTHORIZED_PASSWORD(40002, HttpStatus.BAD_REQUEST, "原密码错误"),

    /** 登录失败统一使用该错误码，不区分「用户不存在 / 密码错误 / 账号禁用」，避免用户枚举。 */
    INVALID_CREDENTIALS(40100, HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    /** 未携带、伪造或过期 JWT。 */
    UNAUTHORIZED(40101, HttpStatus.UNAUTHORIZED, "未认证或登录状态已失效"),

    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权访问该资源"),

    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "请求的资源不存在"),
    ARTICLE_NOT_FOUND(40401, HttpStatus.NOT_FOUND, "文章不存在"),
    ASSET_NOT_FOUND(40402, HttpStatus.NOT_FOUND, "静态资源不存在"),
    FRIEND_LINK_NOT_FOUND(40403, HttpStatus.NOT_FOUND, "友链不存在"),
    GUEST_MESSAGE_NOT_FOUND(40404, HttpStatus.NOT_FOUND, "留言不存在"),

    METHOD_NOT_ALLOWED(40500, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不被支持"),

    /** slug 唯一约束冲突（应用层预检 + 数据库约束兜底）。 */
    SLUG_CONFLICT(40900, HttpStatus.CONFLICT, "文章标识（slug）已存在"),
    DATA_CONFLICT(40901, HttpStatus.CONFLICT, "数据冲突，请检查唯一字段"),
    /** 用户名唯一约束冲突（修改用户名时命中其他账号）。 */
    USERNAME_CONFLICT(40902, HttpStatus.CONFLICT, "用户名已存在"),
    /** 同一站点已有待审核的友链申请。 */
    FRIEND_LINK_DUPLICATE(40903, HttpStatus.CONFLICT, "该站点已有待审核的友链申请"),

    ASSET_TOO_LARGE(41300, HttpStatus.CONTENT_TOO_LARGE, "文件大小超过限制"),
    /** 匿名写接口的请求体超过上限（防止超大 body 打满内存）。 */
    REQUEST_BODY_TOO_LARGE(41301, HttpStatus.CONTENT_TOO_LARGE, "请求体过大"),
    ASSET_TYPE_NOT_ALLOWED(41500, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型"),
    MEDIA_TYPE_NOT_SUPPORTED(41501, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求的 Content-Type 不被支持"),

    /** 友链申请超出当日限额（按 IP 统计）。 */
    FRIEND_LINK_RATE_LIMITED(42900, HttpStatus.TOO_MANY_REQUESTS, "提交过于频繁，请稍后再试"),
    /** 留言提交超出当日限额（按 IP 统计）。 */
    GUEST_MESSAGE_RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS, "留言提交过于频繁，请稍后再试"),

    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),
    STORAGE_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "文件存储失败");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
