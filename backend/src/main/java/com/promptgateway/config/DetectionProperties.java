package com.promptgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 탐지 설정. 대량 기준은 측정 전에 동결한다(design.md 6장). 0 이하이면 ActionDecider가 시작을 막는다. */
@ConfigurationProperties(prefix = "detection")
public record DetectionProperties(@DefaultValue("20") int bulkThreshold) {
}
