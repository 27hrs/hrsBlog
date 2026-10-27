package com.example.hrsblog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "site_setting", uniqueConstraints = @UniqueConstraint(name = "uk_site_setting_group_key", columnNames = {"config_group", "config_key"}))
public class SiteSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_group", nullable = false, length = 64)
    private String configGroup;

    @Column(name = "config_key", nullable = false, length = 128)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "text")
    private String configValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private ValueType valueType = ValueType.STRING;

    @Column(length = 255)
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean publicSetting;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public enum ValueType { STRING, NUMBER, BOOLEAN, JSON }
}
