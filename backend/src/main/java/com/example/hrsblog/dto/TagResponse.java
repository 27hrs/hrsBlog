package com.example.hrsblog.dto;

/** 文章标签的精简表示，只暴露前台需要的名称与 slug。 */
public record TagResponse(String name, String slug) {
}
