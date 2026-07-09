package com.rajaei.codexinspector.core.domain;

public record MigrationChange(String location, String before, String after) {
}
