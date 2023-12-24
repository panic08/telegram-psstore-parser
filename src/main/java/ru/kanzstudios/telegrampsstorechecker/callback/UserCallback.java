package ru.kanzstudios.telegrampsstorechecker.callback;

import lombok.Getter;

@Getter
public abstract class UserCallback {
    public static final String SELECT_UA_ACCOUNTS_CALLBACK_DATA = "select ua accounts callback data";
    public static final String SELECT_TR_ACCOUNTS_CALLBACK_DATA = "select tr accounts callback data";

    public static final String SELECT_ALL_UA_GAMES_ACCOUNTS_CALLBACK_DATA = "select all ua games accounts callback data";
    public static final String SELECT_ALL_TR_GAMES_ACCOUNTS_CALLBACK_DATA = "select all tr games accounts callback data";

    public static final String SELECT_PS4_UA_GAMES_ACCOUNTS_CALLBACK_DATA = "select ps4 ua games accounts callback data";
    public static final String SELECT_PS4_TR_GAMES_ACCOUNTS_CALLBACK_DATA = "select ps4 tr games accounts callback data";

    public static final String SELECT_PS5_UA_GAMES_ACCOUNTS_CALLBACK_DATA = "select ps5 ua games accounts callback data";
    public static final String SELECT_PS5_TR_GAMES_ACCOUNTS_CALLBACK_DATA = "select ps5 tr games accounts callback data";

    public static final String SELECT_ALL_PS_GAME_DATA_CALLBACK_DATA = "select all ps game data callback data";
    public static final String SELECT_GAMES_BY_SEARCH_CALLBACK_DATA = "select games by search callback data";

    public static final String SELECT_FILTERED_GAMES_CALLBACK_DATA = "select filtered games by callback data";
    public static final String SELECT_FILTERED_ALL_PS_GAME_DATA_CALLBACK_DATA = "select filtered all ps game data callback data";

    public static final String ADD_GAME_TO_CART_CALLBACK_DATA = "add game to cart callback data";
    public static final String REMOVE_GAME_FROM_CART_CALLBACK_DATA = "remove game from cart callback data";

}
