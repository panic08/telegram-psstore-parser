package ru.kanzstudios.telegrampsstorechecker.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.kanzstudios.telegrampsstorechecker.pojo.PlaystationStoreGame;
import ru.kanzstudios.telegrampsstorechecker.singleton.AllGamesSingleton;

@Component
@RequiredArgsConstructor
@Slf4j
public class ParseGamesEvent {
    private final RestTemplate restTemplate;
    private static final String PLAYSTATION_STORE_UA_URL = "https://store.playstation.com/ru-ua/pages/browse/%d";
    private static final String PLAYSTATION_STORE_TR_URL = "https://store.playstation.com/en-tr/pages/browse/%d";
    @EventListener(ApplicationReadyEvent.class)
    public void handleParseGamesEvent(){
        new Thread(() -> {
            ResponseEntity<String> pagesResponse = restTemplate.getForEntity(String.format(PLAYSTATION_STORE_UA_URL, 1), String.class);

            Document htmlDocument = Jsoup.parse(pagesResponse.getBody());

            int pageCounts = Integer.parseInt(htmlDocument.getElementsByClass("psw-fill-x ").get(4).text());

            int pages = 1;

            while (pages != pageCounts + 1){
                ResponseEntity<String> pagesResponse1 = restTemplate.getForEntity(String.format(PLAYSTATION_STORE_UA_URL, pages), String.class);

                Document htmlDocument1 = Jsoup.parse(pagesResponse1.getBody());

                Elements gamesElements = htmlDocument1.getElementsByClass("psw-l-w-1/2@mobile-s psw-l-w-1/2@mobile-l psw-l-w-1/6@tablet-l psw-l-w-1/4@tablet-s psw-l-w-1/6@laptop psw-l-w-1/8@desktop psw-l-w-1/8@max");

                loop: for (Element games : gamesElements){
                    if (games.getElementsByClass("psw-t-body psw-c-t-1 psw-t-truncate-2 psw-m-b-2").size() == 0){
                        continue loop;
                    }
                    String gameName = games.getElementsByClass("psw-t-body psw-c-t-1 psw-t-truncate-2 psw-m-b-2").get(0).text();

                    String gameUrl = "https://store.playstation.com" + games.getElementsByClass("psw-link psw-content-link").get(0).attr("href");
                    String gameImageUrl = games.getElementsByClass("psw-blur psw-top-left psw-l-fit-cover").get(0).attr("src");

                    gameImageUrl = gameImageUrl.substring(0, gameImageUrl.length() - 16);

                    ResponseEntity<String> pagesResponse2 = null;

                    try {
                        pagesResponse2 = restTemplate.getForEntity(String.format(gameUrl, pages), String.class);
                    }catch (Exception e){
                        log.warn(e.getMessage());
                        continue;
                    }

                    Document htmlDocument2 = Jsoup.parse(pagesResponse2.getBody());

                    double cost = 0;
                    String costString = null;

                    if (htmlDocument2.getElementsByClass("psw-t-title-m") != null && htmlDocument2.getElementsByClass("psw-t-title-m").size() != 0){
                        costString = htmlDocument2.getElementsByClass("psw-t-title-m").get(0).text();
                    } else if (htmlDocument2.getElementsByClass("psw-t-title-m psw-m-r-4") != null && htmlDocument2.getElementsByClass("psw-t-title-m psw-m-r-4").size() != 0){
                        costString = htmlDocument2.getElementsByClass("psw-t-title-m psw-m-r-4").get(0).text();
                    }

                    if (costString == null || costString.equals("Free") || costString.equals("Бесплатно")){
                        continue loop;
                    }

                    costString = costString.substring(3);

                    costString = costString.replace(" ", "");
                    costString = costString.replace(",", ".");

//                    if (costString.equals("Announced") || costString.equals("явлено")){
//                        continue loop;
//                    }
//
//                    cost  = Double.parseDouble(costString);

                    try {
                        cost  = Double.parseDouble(costString);
                    } catch (Exception e){
                        continue loop;
                    }

                    String playStationVersion = null;

                    if (htmlDocument2.getElementsByClass("psw-p-r-6 psw-p-r-0@tablet-s psw-t-bold psw-l-w-1/2 psw-l-w-1/6@tablet-s psw-l-w-1/6@tablet-l psw-l-w-1/8@laptop psw-l-w-1/6@desktop psw-l-w-1/6@max")
                            .size() != 0){
                        playStationVersion = htmlDocument2.getElementsByClass("psw-p-r-6 psw-p-r-0@tablet-s psw-t-bold psw-l-w-1/2 psw-l-w-1/6@tablet-s psw-l-w-1/6@tablet-l psw-l-w-1/8@laptop psw-l-w-1/6@desktop psw-l-w-1/6@max")
                                .get(0).text();
                    }

                    //System.out.println(playStationVersion);

                    if (playStationVersion != null && playStationVersion.equals("PS5")){
                        AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames().add(PlaystationStoreGame.builder().name(gameName).cost(cost).country("ua").url(gameUrl).psVersion("PS5").imageUrl(gameImageUrl).build());
                    } else if (playStationVersion != null && playStationVersion.equals("PS4, PS5")){
                        AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames().add(PlaystationStoreGame.builder().name(gameName).cost(cost).country("ua").url(gameUrl).psVersion("PS4, PS5").imageUrl(gameImageUrl).build());
                        AllGamesSingleton.getInstance().getAllUaPlaystationFourGames().add(PlaystationStoreGame.builder().name(gameName).cost(cost).country("ua").url(gameUrl).psVersion("PS4, PS5").imageUrl(gameImageUrl).build());
                    } else if (playStationVersion != null && playStationVersion.equals("PS4")){
                        AllGamesSingleton.getInstance().getAllUaPlaystationFourGames().add(PlaystationStoreGame.builder().name(gameName).cost(cost).country("ua").url(gameUrl).psVersion("PS4").imageUrl(gameImageUrl).build());
                    }
                }
                pages++;
            }
        }).start();

        new Thread(() -> {
            ResponseEntity<String> pagesResponse = restTemplate.getForEntity(String.format(PLAYSTATION_STORE_TR_URL, 1), String.class);

            Document htmlDocument = Jsoup.parse(pagesResponse.getBody());

            int pageCounts = Integer.parseInt(htmlDocument.getElementsByClass("psw-fill-x ").get(4).text());

            int pages = 1;

            while (pages != pageCounts + 1){
                ResponseEntity<String> pagesResponse1 = restTemplate.getForEntity(String.format(PLAYSTATION_STORE_TR_URL, pages), String.class);

                Document htmlDocument1 = Jsoup.parse(pagesResponse1.getBody());

                Elements gamesElements = htmlDocument1.getElementsByClass("psw-l-w-1/2@mobile-s psw-l-w-1/2@mobile-l psw-l-w-1/6@tablet-l psw-l-w-1/4@tablet-s psw-l-w-1/6@laptop psw-l-w-1/8@desktop psw-l-w-1/8@max");

                loop: for (Element games : gamesElements){
                    if (games.getElementsByClass("psw-t-body psw-c-t-1 psw-t-truncate-2 psw-m-b-2").size() == 0){
                        break;
                    }
                    String gameName = games.getElementsByClass("psw-t-body psw-c-t-1 psw-t-truncate-2 psw-m-b-2").get(0).text();

                    String gameUrl = "https://store.playstation.com" + games.getElementsByClass("psw-link psw-content-link").get(0).attr("href");
                    String gameImageUrl = games.getElementsByClass("psw-blur psw-top-left psw-l-fit-cover").get(0).attr("src");

                    gameImageUrl = gameImageUrl.substring(0, gameImageUrl.length() - 16);

                    ResponseEntity<String> pagesResponse2 = null;

                    try {
                        pagesResponse2 = restTemplate.getForEntity(String.format(gameUrl, pages), String.class);
                    }catch (Exception e){
                        continue;
                    }

                    Document htmlDocument2 = Jsoup.parse(pagesResponse2.getBody());

                    double cost = 0;
                    String costString = null;

                    if (htmlDocument2.getElementsByClass("psw-t-title-m") != null && htmlDocument2.getElementsByClass("psw-t-title-m").size() != 0){
                        costString = htmlDocument2.getElementsByClass("psw-t-title-m").get(0).text();
                    } else if (htmlDocument2.getElementsByClass("psw-t-title-m psw-m-r-4") != null && htmlDocument2.getElementsByClass("psw-t-title-m psw-m-r-4").size() != 0){
                        costString = htmlDocument2.getElementsByClass("psw-t-title-m psw-m-r-4").get(0).text();
                    }

                    if (costString == null || costString.equals("Free") || costString.equals("Бесплатно")){
                        continue loop;
                    }
                    costString = costString.split(" ")[0];

                    costString = costString.replace(".", "");
                    costString = costString.replace(",", ".");

//                    if (costString.equals("Announced") || costString.equals("Included") || costString.equals("Not")){
//                        continue loop;
//                    }
//                    cost  = Double.parseDouble(costString);

                    try {
                        cost  = Double.parseDouble(costString);
                    } catch (Exception e){
                        log.warn(e.getMessage());
                        continue loop;
                    }

                    String playStationVersion = null;

                    if (htmlDocument2.getElementsByClass("psw-p-r-6 psw-p-r-0@tablet-s psw-t-bold psw-l-w-1/2 psw-l-w-1/6@tablet-s psw-l-w-1/6@tablet-l psw-l-w-1/8@laptop psw-l-w-1/6@desktop psw-l-w-1/6@max")
                            .size() != 0){
                        playStationVersion = htmlDocument2.getElementsByClass("psw-p-r-6 psw-p-r-0@tablet-s psw-t-bold psw-l-w-1/2 psw-l-w-1/6@tablet-s psw-l-w-1/6@tablet-l psw-l-w-1/8@laptop psw-l-w-1/6@desktop psw-l-w-1/6@max")
                                .get(0).text();
                    }

                    if (playStationVersion != null && playStationVersion.equals("PS5")){
                        AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames().add(PlaystationStoreGame.builder().name(gameName).cost(cost).country("tr").url(gameUrl).psVersion("PS5").imageUrl(gameImageUrl).build());
                    } else if (playStationVersion != null && playStationVersion.equals("PS4, PS5")){
                        AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames().add(PlaystationStoreGame.builder().name(gameName).cost(cost).country("tr").url(gameUrl).psVersion("PS4, PS5").imageUrl(gameImageUrl).build());
                        AllGamesSingleton.getInstance().getAllTrPlaystationFourGames().add(PlaystationStoreGame.builder().name(gameName).cost(cost).country("tr").url(gameUrl).psVersion("PS4, PS5").imageUrl(gameImageUrl).build());
                    } else if (playStationVersion != null && playStationVersion.equals("PS4")){
                        AllGamesSingleton.getInstance().getAllTrPlaystationFourGames().add(PlaystationStoreGame.builder().name(gameName).cost(cost).country("tr").url(gameUrl).psVersion("PS4").imageUrl(gameImageUrl).build());
                    }

                    //System.out.println(gameName + " " + gameUrl + " " + gameImageUrl + " version: " + playStationVersion);
                }
                pages++;
            }
        }).start();
    }
}
