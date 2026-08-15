package com.fimobook.backend;

public record CardVisualTheme(String ovr, String position, String name) {

    public static final CardVisualTheme DEFAULT = new CardVisualTheme("#ffffff", "#ffffff", "#ffffff");
}
