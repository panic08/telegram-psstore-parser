package ru.kanzstudios.telegrampsstorechecker.singleton;

import lombok.Data;
import ru.kanzstudios.telegrampsstorechecker.pojo.PlaystationStoreGame;

import java.util.ArrayList;
import java.util.List;

@Data
public class AllGamesSingleton {
    private static AllGamesSingleton allGamesSingleton;
    private List<PlaystationStoreGame> allUaPlaystationFiveGames = new ArrayList<>();
    private List<PlaystationStoreGame> allUaPlaystationFourGames = new ArrayList<>();
    private List<PlaystationStoreGame> allTrPlaystationFiveGames = new ArrayList<>();
    private List<PlaystationStoreGame> allTrPlaystationFourGames = new ArrayList<>();

    public static AllGamesSingleton getInstance(){
        if (allGamesSingleton == null) allGamesSingleton = new AllGamesSingleton();

        return allGamesSingleton;
    }
}
