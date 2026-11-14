package com.example.hrsblog.service;

/**
 * 静态资源存储抽象：首版落本地磁盘，后续可替换为对象存储（OSS/S3）而不影响业务层。
 */
public interface StaticStorageService {

    /** 写入文件，存储 key 对应的相对路径（实现需自行防御路径穿越）。 */
    void store(String storageKey, byte[] content);

    /** 删除文件，文件不存在时视为成功（幂等）。 */
    void delete(String storageKey);

    /** 文件是否已存在，用于秒传时避免重复写盘。 */
    boolean exists(String storageKey);

    /** 生成对外访问链接。 */
    String urlOf(String storageKey);
}
