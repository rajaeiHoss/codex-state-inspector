package com.rajaei.codexinspector.core.domain;

public record ChecksumResult(String path, String type, long sizeBytes, String sha256) {
}
