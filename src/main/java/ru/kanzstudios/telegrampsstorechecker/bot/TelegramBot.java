package ru.kanzstudios.telegrampsstorechecker.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.kanzstudios.telegrampsstorechecker.callback.AdminCallback;
import ru.kanzstudios.telegrampsstorechecker.callback.BackCallback;
import ru.kanzstudios.telegrampsstorechecker.callback.UserCallback;
import ru.kanzstudios.telegrampsstorechecker.model.Coefficient;
import ru.kanzstudios.telegrampsstorechecker.model.Member;
import ru.kanzstudios.telegrampsstorechecker.pojo.CustomPair;
import ru.kanzstudios.telegrampsstorechecker.pojo.PlaystationStoreGame;
import ru.kanzstudios.telegrampsstorechecker.property.TelegramBotProperty;
import ru.kanzstudios.telegrampsstorechecker.repository.AdminRepository;
import ru.kanzstudios.telegrampsstorechecker.repository.CoefficientRepository;
import ru.kanzstudios.telegrampsstorechecker.repository.MemberRepository;
import ru.kanzstudios.telegrampsstorechecker.singleton.AllGamesSingleton;
import ru.kanzstudios.telegrampsstorechecker.util.UrlFileDownloader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private TelegramBotProperty telegramBotProperty;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private CoefficientRepository coefficientRepository;

    private static final Map<Long, CustomPair<String, Integer>> idCountryPsVersionMap = new HashMap<>();
    private static final Map<Long, List<PlaystationStoreGame>> idCartMap = new HashMap<>();
    private static final Set<Long> adminDispatchStepsMap = new HashSet<>();
    private static final Map<Long, CustomPair<String, String>> adminChangeCoefficientStepsMap = new HashMap<>();

    public TelegramBot(TelegramBotProperty telegramBotProperty) {
        this.telegramBotProperty = telegramBotProperty;
        List<BotCommand> listOfCommands = new ArrayList<>();

        listOfCommands.add(new BotCommand("/start", "Перезапустить"));
        listOfCommands.add(new BotCommand("/shop", "Магазин"));
        listOfCommands.add(new BotCommand("/cart", "Корзина"));
        listOfCommands.add(new BotCommand("/games", "Купить игры"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return telegramBotProperty.getName();
    }

    @Override
    public String getBotToken() {
        return telegramBotProperty.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        new Thread(() -> {

            if (update.hasMessage() && update.getMessage().hasText()){
                String text = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();

                if (memberRepository.findByTelegramUserId(chatId) == null){
                    memberRepository.save(Member.builder().telegramUserId(chatId).build());
                }

                switch (text){
                    case "/start" -> {

                        InputStream photoStream = getClass().getClassLoader().getResourceAsStream("greeting.jpg");

                        InputFile photoInputFile = new InputFile(photoStream, "greeting.jpg");

                        SendPhoto photoMessage = SendPhoto.builder()
                                .chatId(chatId)
                                .caption("\uD83E\uDD16\uD83D\uDCAC <b>Привет! Я бот-калькулятор, был создан для того, чтобы Вы могли легко узнавать цены на игры!</b>\n\n"
                                + "Суть моей работы проста: Добавляете интересующие Вас игры в корзину \uD83D\uDED2 а я посчитаю на какую сумму это все выйдет.\n\n"
                                + "\uD83C\uDFAE В моем ассортименте больше 9000 игр, Вы точно найдете то, что ищете \uD83C\uDFAE \n\n"
                                + "Работаю с \uD83C\uDDFA\uD83C\uDDE6 украинским и \uD83C\uDDF9\uD83C\uDDF7 турецким PlayStation Store!")
                                .photo(photoInputFile)
                                .replyMarkup(getDefaultKeyboardMarkup(chatId))
                                .parseMode("html")
                                .build();

                        SendMessage message = SendMessage.builder()
                                .chatId(chatId)
                                .text("Нажмите на кнопку \"\uD83D\uDCB0 Магазин\", чтобы начать пользоваться ботом")
                                .parseMode("html")
                                .build();

                        try {
                            execute(photoMessage);
                            execute(message);

                            photoStream.close();
                        } catch (TelegramApiException | IOException e){
                            log.warn(e.getMessage());
                        }


                        return;
                    }
                    
                    case "/shop", "\uD83D\uDCB0 Магазин" -> {
                        sendShopMessage(chatId);

                        return;
                    }

                    case "\uD83D\uDC5B Купить игры", "/games" -> {
                        SendMessage message = SendMessage.builder()
                                .text("<b>Хей</b> \uD83D\uDC4B \n\n"
                                        + "Вижу ты хочешь купить новых игрушек?\n\n"
                                        + "Перешли пожалуйста сообщение, где указаны все игры в Вашей корзине и их итоговая стоимость в телеграмм @kanz_owner\n\n"
                                        + "Я отвечу сразу, как смогу, чтобы помочь их приобрести - Виталий (Цаль стор)")
                                .chatId(chatId)
                                .parseMode("html")
                                .replyMarkup(getDefaultKeyboardMarkup(chatId))
                                .build();

                        try {
                            execute(message);
                        } catch (TelegramApiException e){
                            log.warn(e.getMessage());
                        }
                        return;
                    }
                    case "/cart", "\uD83D\uDED2 Корзина" -> {
                        sendCartMessage(chatId);
                        return;
                    }

                    case "\uD83D\uDED1 Админ-панель" -> {
                        sendAdminMessage(chatId);
                        return;
                    }

                }

                if (idCountryPsVersionMap.get(chatId) != null){
                    CustomPair<String, Integer> countryPsVersion = idCountryPsVersionMap.get(chatId);

                    List<PlaystationStoreGame> playstationStoreGames = new ArrayList<>();
                    List<PlaystationStoreGame> filteredPlaystationStoreGames = new ArrayList<>();

                    if (countryPsVersion.getTuple1().equals("ua")){
                        if (countryPsVersion.getTuple2() == 4){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFourGames());
                        } else if (countryPsVersion.getTuple2() == 5) {
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames());
                        } else if (countryPsVersion.getTuple2() == 6) {
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames());

                            List<PlaystationStoreGame> playstationStoreGameList = AllGamesSingleton.getInstance().getAllUaPlaystationFourGames();

                            for (PlaystationStoreGame playstationStoreGame : playstationStoreGameList){
                                if (playstationStoreGame.getPsVersion().equals("PS4")){
                                    playstationStoreGames.add(playstationStoreGame);
                                }
                            }
                        }
                    } else if (countryPsVersion.getTuple1().equals("tr")){
                        if (countryPsVersion.getTuple2() == 4){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFourGames());
                        } else if (countryPsVersion.getTuple2() == 5){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames());
                        } else if (countryPsVersion.getTuple2() == 6) {
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames());

                            List<PlaystationStoreGame> playstationStoreGameList = AllGamesSingleton.getInstance().getAllTrPlaystationFourGames();

                            for (PlaystationStoreGame playstationStoreGame : playstationStoreGameList){
                                if (playstationStoreGame.getPsVersion().equals("PS4")){
                                    playstationStoreGames.add(playstationStoreGame);
                                }
                            }
                        }
                    }

                    for (PlaystationStoreGame key : playstationStoreGames){
                        if (key.getName().contains(text)){
                            filteredPlaystationStoreGames.add(key);
                        }
                    }

                    if (filteredPlaystationStoreGames.size() == 0){
                        SendMessage message = SendMessage.builder()
                                .text("<b>Поиск:</b> " + text + "\n"
                                + "<b>Ничего не найдено, попробуйте написать по другому</b>")
                                .chatId(chatId)
                                .parseMode("html")
                                .build();

                        try {
                            execute(message);
                        } catch (TelegramApiException e){
                            log.warn(e.getMessage());
                        }

                        return;
                    }

                    idCountryPsVersionMap.remove(chatId);

                    sendFilteredPsMessage(chatId, 1, countryPsVersion.getTuple2(), countryPsVersion.getTuple1(),
                            text, filteredPlaystationStoreGames);

                    return;
                }

                if (adminDispatchStepsMap.contains(chatId)){
                    adminDispatchStepsMap.remove(chatId);

                    SendMessage newMessage = SendMessage.builder()
                            .text("✅ <b>Вы успешно совершили рассылку пользователям</b>")
                            .chatId(chatId)
                            .parseMode("html")
                            .build();

                    try {
                        execute(newMessage);
                    } catch (TelegramApiException e){
                        log.warn(e.getMessage());
                    }

                    memberRepository.findAll().forEach(m -> {
                        SendMessage dispatchMessage = SendMessage.builder()
                                .text(text)
                                .chatId(m.getTelegramUserId())
                                .parseMode("html")
                                .build();

                        try {
                            execute(dispatchMessage);
                        } catch (TelegramApiException ignored){
                        }
                    });

                    return;
                }

                if (adminChangeCoefficientStepsMap.get(chatId) != null){
                    CustomPair<String, String> rangeCountryPair = adminChangeCoefficientStepsMap.get(chatId);

                    double coefficient = Double.parseDouble(text);
                    String range = rangeCountryPair.getTuple1();
                    String country = rangeCountryPair.getTuple2();

                    coefficientRepository.updateCoefficientByRangeAndCountry(coefficient, range, country);

                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CHANGE_COEFFICIENT_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

                    keyboardButtonsRow.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow);

                    inlineKeyboardMarkup.setKeyboard(rowList);

                    SendMessage message = SendMessage.builder()
                            .text("✅ <b>Вы успешно установили коэффициент</b> " + coefficient + " <b>для страны</b> " + country + " <b>с диапазоном</b> " + range)
                            .chatId(chatId)
                            .replyMarkup(inlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e){
                        log.warn(e.getMessage());
                    }

                    return;
                }
            } else if (update.hasCallbackQuery()){
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                int messageId = update.getCallbackQuery().getMessage().getMessageId();
                String callbackQueryData = update.getCallbackQuery().getData();

                if (memberRepository.findByTelegramUserId(chatId) == null){
                    memberRepository.save(Member.builder().telegramUserId(chatId).build());
                }

                switch (callbackQueryData){
                    case UserCallback.SELECT_UA_ACCOUNTS_CALLBACK_DATA, BackCallback.BACK_TO_UA_SHOP_CALLBACK_DATA -> {
                        editShopMessage(chatId, messageId, "ua");
                        return;
                    }
                    case UserCallback.SELECT_TR_ACCOUNTS_CALLBACK_DATA, BackCallback.BACK_TO_TR_SHOP_CALLBACK_DATA -> {
                        editShopMessage(chatId, messageId, "tr");
                        return;
                    }
                    case BackCallback.BACK_TO_REGION_CALLBACK_DATA -> {
                        editCategoryShopMessage(chatId, messageId);
                        return;
                    }
                    case BackCallback.BACK_TO_DELETE_CALLBACK_DATA -> {
                        deleteMessage(chatId, messageId);
                        return;
                    }
                    case BackCallback.BACK_TO_CANCEL_SEARCH_CALLBACK_DATA -> {
                        idCountryPsVersionMap.remove(chatId);
                        deleteMessage(chatId, messageId);
                        return;
                    }
                    case BackCallback.BACK_TO_ADMIN_CALLBACK_DATA -> {
                        adminDispatchStepsMap.remove(chatId);

                        editAdminMessage(chatId, messageId);
                        return;
                    }
                    case BackCallback.BACK_TO_ADMIN_CHANGE_COEFFICIENT_CALLBACK_DATA -> {
                        adminChangeCoefficientStepsMap.remove(chatId);

                        editCoefficientMessage(chatId, messageId);
                        return;
                    }
                    case AdminCallback.ADMIN_DISPATCH_CALLBACK_DATA -> {
                        adminDispatchStepsMap.add(chatId);

                        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                        InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                                .text("◀\uFE0F Назад")
                                .build();

                        List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

                        keyboardButtonsRow.add(backToAdminButton);

                        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                        rowList.add(keyboardButtonsRow);

                        inlineKeyboardMarkup.setKeyboard(rowList);

                        EditMessageText editMessage = EditMessageText.builder()
                                .text("\uD83D\uDCE3 <b>Отправьте сообщение, которое вы хотите разослать всем пользователям</b>")
                                .chatId(chatId)
                                .replyMarkup(inlineKeyboardMarkup)
                                .messageId(messageId)
                                .parseMode("html")
                                .build();

                        try {
                            execute(editMessage);
                        } catch (TelegramApiException e){
                            log.warn(e.getMessage());
                        }
                        return;
                    }
                    case AdminCallback.ADMIN_CHANGE_COEFFICIENT_CALLBACK_DATA -> {
                        editCoefficientMessage(chatId, messageId);
                        return;
                    }
                }

                if (callbackQueryData.contains(UserCallback.SELECT_ALL_UA_GAMES_ACCOUNTS_CALLBACK_DATA)){
                    int page = Integer.parseInt(callbackQueryData.split(" ")[7]);

                    editAllGames(chatId, messageId, page, "ua");
                    return;
                }

                if (callbackQueryData.contains(UserCallback.SELECT_PS4_UA_GAMES_ACCOUNTS_CALLBACK_DATA)){
                    int page = Integer.parseInt(callbackQueryData.split(" ")[7]);

                    editPsMessage(chatId, messageId, page, 4, "ua");
                    return;
                }
                if (callbackQueryData.contains(UserCallback.SELECT_PS5_UA_GAMES_ACCOUNTS_CALLBACK_DATA)){
                    int page = Integer.parseInt(callbackQueryData.split(" ")[7]);

                    editPsMessage(chatId, messageId, page, 5, "ua");
                    return;
                }

                if (callbackQueryData.contains(UserCallback.SELECT_ALL_TR_GAMES_ACCOUNTS_CALLBACK_DATA)){
                    int page = Integer.parseInt(callbackQueryData.split(" ")[7]);

                    editAllGames(chatId, messageId, page, "tr");
                    return;
                }

                if (callbackQueryData.contains(UserCallback.SELECT_PS4_TR_GAMES_ACCOUNTS_CALLBACK_DATA)){
                    int page = Integer.parseInt(callbackQueryData.split(" ")[7]);

                    editPsMessage(chatId, messageId, page, 4, "tr");
                    return;
                }
                if (callbackQueryData.contains(UserCallback.SELECT_PS5_TR_GAMES_ACCOUNTS_CALLBACK_DATA)){
                    int page = Integer.parseInt(callbackQueryData.split(" ")[7]);

                    editPsMessage(chatId, messageId, page, 5, "tr");
                    return;
                }

                if (callbackQueryData.contains(UserCallback.SELECT_ALL_PS_GAME_DATA_CALLBACK_DATA)){
                    String[] callbackSplit = callbackQueryData.split(" ");

                    String psVersion = callbackSplit[9];

                    if (callbackSplit.length == 11){
                        psVersion += " " + callbackSplit[10];
                    }
                    String country = callbackSplit[8];
                    int id = Integer.parseInt(callbackSplit[7]);

                    List<PlaystationStoreGame> playstationStoreGameList = new ArrayList<>();

                    if (country.equals("ua")){
                        if (psVersion.equals("4")){
                            playstationStoreGameList.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFourGames());
                        } else if (psVersion.equals("6")){
                            playstationStoreGameList.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames());

                            List<PlaystationStoreGame> playstationStoreGames = AllGamesSingleton.getInstance().getAllUaPlaystationFourGames();

                            for (PlaystationStoreGame playstationStoreGame : playstationStoreGames){
                                if (playstationStoreGame.getPsVersion().equals("PS4")){
                                    playstationStoreGameList.add(playstationStoreGame);
                                }
                            }
                        } else {
                            playstationStoreGameList.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames());
                        }
                    } else if (country.equals("tr")){
                        if (psVersion.equals("4")){
                            playstationStoreGameList.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFourGames());
                        } else if (psVersion.equals("6")){
                            playstationStoreGameList.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames());

                            List<PlaystationStoreGame> playstationStoreGames = AllGamesSingleton.getInstance().getAllTrPlaystationFourGames();

                            for (PlaystationStoreGame playstationStoreGame : playstationStoreGames){
                                if (playstationStoreGame.getPsVersion().equals("PS4")){
                                    playstationStoreGameList.add(playstationStoreGame);
                                }
                            }
                        } else {
                            playstationStoreGameList.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames());
                        }
                    }

                    sendPlayStoreGameData(chatId, playstationStoreGameList.get(id), country);
                    return;
                }

                if (callbackQueryData.contains(UserCallback.SELECT_GAMES_BY_SEARCH_CALLBACK_DATA)){
                    String[] stringsSplit = callbackQueryData.split(" ");

                    String country = stringsSplit[6];
                    int psVersion = Integer.parseInt(stringsSplit[7]);

                    idCountryPsVersionMap.put(chatId, new CustomPair<>(country, psVersion));

                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton cancelSearchButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_CANCEL_SEARCH_CALLBACK_DATA)
                            .text("❌ Отменить поиск")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

                    keyboardButtonsRow.add(cancelSearchButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow);

                    inlineKeyboardMarkup.setKeyboard(rowList);

                    SendMessage message = SendMessage.builder()
                            .text("<b>Введите полное название игры или ее часть.</b>\n\n<i>(Например: NHL или Hogwarts Legacy)</i>")
                            .chatId(chatId)
                            .parseMode("html")
                            .replyMarkup(inlineKeyboardMarkup)
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e){
                        e.printStackTrace();
                    }

                    return;
                }

                if (callbackQueryData.contains(UserCallback.SELECT_FILTERED_GAMES_CALLBACK_DATA)){
                    String[] strings = callbackQueryData.split(" ");

                    int page = Integer.parseInt(strings[6]);
                    int psVersion = Integer.parseInt(strings[7]);
                    String country = strings[8];

                    String grater = strings[9];

                    String text = update.getCallbackQuery().getMessage().getText().split(" ", 2)[1];


                    List<PlaystationStoreGame> playstationStoreGames = new ArrayList<>();
                    List<PlaystationStoreGame> filteredPlaystationStoreGames = new ArrayList<>();

                    if (country.equals("ua")){
                        if (psVersion == 4){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFourGames());
                        } else if (psVersion == 5){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames());
                        } else if (psVersion == 6){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames());

                            List<PlaystationStoreGame> playstationStoreGameList = AllGamesSingleton.getInstance().getAllUaPlaystationFourGames();

                            for (PlaystationStoreGame playstationStoreGame : playstationStoreGameList){
                                if (playstationStoreGame.getPsVersion().equals("PS4")){
                                    playstationStoreGames.add(playstationStoreGame);
                                }
                            }
                        }
                    } else if (country.equals("tr")){
                        if (psVersion == 4){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFourGames());
                        } else if (psVersion == 5){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames());
                        } else if (psVersion == 6){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames());

                            List<PlaystationStoreGame> playstationStoreGameList = AllGamesSingleton.getInstance().getAllTrPlaystationFourGames();

                            for (PlaystationStoreGame playstationStoreGame : playstationStoreGameList){
                                if (playstationStoreGame.getPsVersion().equals("PS4")){
                                    playstationStoreGames.add(playstationStoreGame);
                                }
                            }
                        }
                    }

                    for (PlaystationStoreGame key : playstationStoreGames){
                        if (key.getName().contains(text)){
                            filteredPlaystationStoreGames.add(key);
                        }
                    }

                    editFilteredPsMessage(chatId, messageId, (grater.equals("+") ? page + 1 : page - 1), psVersion, country, text, filteredPlaystationStoreGames);
                    return;
                }


                if (callbackQueryData.contains(UserCallback.SELECT_FILTERED_ALL_PS_GAME_DATA_CALLBACK_DATA)){
                    String[] strings = callbackQueryData.split(" ");

                    int index = Integer.parseInt(strings[8]);
                    int psVersion = Integer.parseInt(strings[10]);
                    String country = strings[9];

                    String text = update.getCallbackQuery().getMessage().getText().split(" ", 2)[1];

                    List<PlaystationStoreGame> playstationStoreGames = new ArrayList<>();
                    List<PlaystationStoreGame> filteredPlaystationStoreGames = new ArrayList<>();

                    if (country.equals("ua")){
                        if (psVersion == 4){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFourGames());
                        } else if (psVersion == 5){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames());
                        } else if (psVersion == 6){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames());

                            List<PlaystationStoreGame> playstationStoreGameList = AllGamesSingleton.getInstance().getAllUaPlaystationFourGames();

                            for (PlaystationStoreGame playstationStoreGame : playstationStoreGameList){
                                if (playstationStoreGame.getPsVersion().equals("PS4")){
                                    playstationStoreGames.add(playstationStoreGame);
                                }
                            }
                        }
                    } else if (country.equals("tr")){
                        if (psVersion == 4){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFourGames());
                        } else if (psVersion == 5){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames());
                        } else if (psVersion == 6){
                            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames());

                            List<PlaystationStoreGame> playstationStoreGameList = AllGamesSingleton.getInstance().getAllTrPlaystationFourGames();

                            for (PlaystationStoreGame playstationStoreGame : playstationStoreGameList){
                                if (playstationStoreGame.getPsVersion().equals("PS4")){
                                    playstationStoreGames.add(playstationStoreGame);
                                }
                            }
                        }
                    }

                    for (PlaystationStoreGame key : playstationStoreGames){
                        if (key.getName().contains(text)){
                            filteredPlaystationStoreGames.add(key);
                        }
                    }


                    sendPlayStoreGameData(chatId, filteredPlaystationStoreGames.get(index), country);
                    return;
                }


                if (callbackQueryData.contains(UserCallback.ADD_GAME_TO_CART_CALLBACK_DATA)){
                    String[] stringsSplit = callbackQueryData.split(" ");

                    String country = stringsSplit[6];
                    String psVersion = stringsSplit[7];
                    String caption = update.getCallbackQuery().getMessage().getCaption();
                    StringBuilder gameName = new StringBuilder();

                    String[] captionStringsSplit = caption.split(" ");

                    for (int i = 2; i < captionStringsSplit.length; i++){
                        if (!captionStringsSplit[i].equals("\uD83C\uDFAE")){
                            if (captionStringsSplit[i + 1].equals("\uD83C\uDFAE")){
                                gameName.append(captionStringsSplit[i]);
                            } else {
                                gameName.append(captionStringsSplit[i]).append(" ");
                            }
                        } else {
                            break;
                        }
                    }

                    List<PlaystationStoreGame> playstationStoreGameList = null;

                    if (country.equals("ua")){
                        if (psVersion.equals("PS4")){
                            playstationStoreGameList = AllGamesSingleton.getInstance().getAllUaPlaystationFourGames();;
                        } else {
                            playstationStoreGameList = AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames();
                        }
                    } else if (country.equals("tr")){
                        if (psVersion.equals("PS4")){
                            playstationStoreGameList = AllGamesSingleton.getInstance().getAllTrPlaystationFourGames();;
                        } else {
                            playstationStoreGameList = AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames();
                        }
                    }

                    List<PlaystationStoreGame> cartGames = idCartMap.get(chatId);

                    if (cartGames == null){
                        cartGames = new ArrayList<>();
                    }

                    for (PlaystationStoreGame playstationStoreGame : playstationStoreGameList){
                        if (playstationStoreGame.getName().contentEquals(gameName)){
                            cartGames.add(playstationStoreGame);
                        }
                    }

                    idCartMap.put(chatId, cartGames);

                    AnswerCallbackQuery answerCallback = AnswerCallbackQuery.builder()
                            .callbackQueryId(update.getCallbackQuery().getId())
                            .text("Вы успешно добавили игру " + gameName + " в корзину")
                            .showAlert(false)
                            .build();

                    SendMessage message = SendMessage.builder()
                            .text("✅ <b>Игра</b> " + gameName + " <b>успешно добавлена в корзину</b> ✅")
                            .chatId(chatId)
                            .parseMode("html")
                            .build();

                    try {
                        execute(answerCallback);
                        execute(message);
                    } catch (TelegramApiException e){
                        log.warn(e.getMessage());
                    }

                    return;
                }

                if (callbackQueryData.contains(UserCallback.REMOVE_GAME_FROM_CART_CALLBACK_DATA)){
                    String idString = callbackQueryData.split(" ")[6];

                    if (idString.equals("*")){
                        idCartMap.remove(chatId);
                    } else {
                        List<PlaystationStoreGame> playstationStoreGameList = idCartMap.get(chatId);

                        int id = Integer.parseInt(idString);

                        playstationStoreGameList.remove(id);
                    }

                    editCartMessage(chatId, messageId);
                    return;
                }

                if (callbackQueryData.contains(AdminCallback.ADMIN_CHANGE_COEFFICIENT_VALUE_CALLBACK_DATA)){
                    String range = callbackQueryData.split(" ")[6];
                    String country = callbackQueryData.split(" ")[7];

                    adminChangeCoefficientStepsMap.put(chatId, new CustomPair<>(range, country));

                    double establishedCoefficient = coefficientRepository.findCoefficientByRangeAndCountry(range, country);

                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CHANGE_COEFFICIENT_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

                    keyboardButtonsRow.add(backButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow);

                    inlineKeyboardMarkup.setKeyboard(rowList);

                    EditMessageText editMessage = EditMessageText.builder()
                            .text("\uD83D\uDD04 <b>Укажите коэффициент для диапазона</b> " + range + " <b>и страны</b> " + country + "<b>, на который будут умножаться цены и преобразоваться в рубль</b>\n\n"
                            + "<b>Установленный коэффициент для этой позиции:</b> " + establishedCoefficient)
                            .chatId(chatId)
                            .replyMarkup(inlineKeyboardMarkup)
                            .messageId(messageId)
                            .parseMode("html")
                            .build();

                    try {
                        execute(editMessage);
                    } catch (TelegramApiException e){
                        log.warn(e.getMessage());
                    }
                    return;
                }

            } else if (update.hasMessage() && update.getMessage().hasPhoto()){
                long chatId =  update.getMessage().getChatId();
                String caption = update.getMessage().getCaption();
                PhotoSize photo  = update.getMessage().getPhoto().get(2);

                if (adminDispatchStepsMap.contains(chatId)){
                    adminDispatchStepsMap.remove(chatId);

                    SendMessage newMessage = SendMessage.builder()
                            .text("✅ <b>Вы успешно совершили рассылку пользователям</b>")
                            .chatId(chatId)
                            .parseMode("html")
                            .build();

                    try {
                        execute(newMessage);
                    } catch (TelegramApiException e){
                        log.warn(e.getMessage());
                    }

                    GetFile getFile = GetFile.builder()
                            .fileId(photo.getFileId())
                            .build();

                    String URL = null;
                    File file = null;

                    try {
                        URL = execute(getFile).getFileUrl(telegramBotProperty.getToken());

                        file = UrlFileDownloader.downloadTempFile(URL, "photo", ".jpg");
                    } catch (Exception e){
                        log.warn(e.getMessage());
                    }

                    File finalFile = file;

                    memberRepository.findAll().forEach(m -> {
                        SendPhoto dispatchMessage = SendPhoto.builder()
                                .caption(caption)
                                .photo(new InputFile(finalFile))
                                .chatId(m.getTelegramUserId())
                                .parseMode("html")
                                .build();

                        try {
                            execute(dispatchMessage);
                        } catch (TelegramApiException ignored){
                        }
                    });

                    file.delete();

                    return;
                }
            }

        }).start();
    }

    private ReplyKeyboardMarkup getDefaultKeyboardMarkup(long chatId){
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        KeyboardButton shopButton = new KeyboardButton("\uD83D\uDCB0 Магазин");
        KeyboardButton cartButton = new KeyboardButton("\uD83D\uDED2 Корзина");
        KeyboardButton buyGamesButton = new KeyboardButton("\uD83D\uDC5B Купить игры");

        KeyboardRow keyboardRow1 = new KeyboardRow();
        KeyboardRow keyboardRow2 = new KeyboardRow();
        KeyboardRow keyboardRow3 = new KeyboardRow();

        keyboardRow1.add(shopButton);
        keyboardRow2.add(cartButton);
        keyboardRow3.add(buyGamesButton);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        keyboardRows.add(keyboardRow1);
        keyboardRows.add(keyboardRow2);
        keyboardRows.add(keyboardRow3);

        if (adminRepository.findByTelegramUserId(chatId) != null){
            KeyboardButton adminButton = new KeyboardButton("\uD83D\uDED1 Админ-панель");

            KeyboardRow keyboardRow = new KeyboardRow();

            keyboardRow.add(adminButton);

            keyboardRows.add(keyboardRow);
        }

        keyboardMarkup.setKeyboard(keyboardRows);

        return keyboardMarkup;
    }

    private void sendShopMessage(long chatId){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton ukPsStoreButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SELECT_UA_ACCOUNTS_CALLBACK_DATA)
                .text("\uD83C\uDDFA\uD83C\uDDE6 Украинский PS Store")
                .build();

        InlineKeyboardButton tkPsStoreButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SELECT_TR_ACCOUNTS_CALLBACK_DATA)
                .text("\uD83C\uDDF9\uD83C\uDDF7 Турецкий PS Store")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(ukPsStoreButton);
        keyboardButtonsRow2.add(tkPsStoreButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("✔\uFE0F <b>Выберите регион PlayStation для поиска</b>")
                .replyMarkup(inlineKeyboardMarkup)
                .chatId(chatId)
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }
    private void editCategoryShopMessage(long chatId, int messageId){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton ukPsStoreButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SELECT_UA_ACCOUNTS_CALLBACK_DATA)
                .text("\uD83C\uDDFA\uD83C\uDDE6 Украинский PS Store")
                .build();

        InlineKeyboardButton tkPsStoreButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SELECT_TR_ACCOUNTS_CALLBACK_DATA)
                .text("\uD83C\uDDF9\uD83C\uDDF7 Турецкий PS Store")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(ukPsStoreButton);
        keyboardButtonsRow2.add(tkPsStoreButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessage = EditMessageText.builder()
                .text("✔\uFE0F <b>Выберите регион PlayStation для поиска</b>")
                .replyMarkup(inlineKeyboardMarkup)
                .messageId(messageId)
                .chatId(chatId)
                .parseMode("html")
                .build();

        try {
            execute(editMessage);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }
    private void editAllGames(long chatId, int messageId, int page, String country){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        List<PlaystationStoreGame> playstationStoreGames = new ArrayList<>();

        String callback = null;

        if (country.equals("ua")){
            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames());

            List<PlaystationStoreGame> playstationStoreGameList = AllGamesSingleton.getInstance().getAllUaPlaystationFourGames();;

            for (PlaystationStoreGame playstationStoreGame : playstationStoreGameList){
                if (playstationStoreGame.getPsVersion().equals("PS4")){
                    playstationStoreGames.add(playstationStoreGame);
                }
            }
            callback = UserCallback.SELECT_ALL_UA_GAMES_ACCOUNTS_CALLBACK_DATA;
        } else if (country.equals("tr")){
            playstationStoreGames.addAll(AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames());

            List<PlaystationStoreGame> playstationStoreGameList = AllGamesSingleton.getInstance().getAllTrPlaystationFourGames();;

            for (PlaystationStoreGame playstationStoreGame : playstationStoreGameList){
                if (playstationStoreGame.getPsVersion().equals("PS4")){
                    playstationStoreGames.add(playstationStoreGame);
                }
            }

            callback = UserCallback.SELECT_ALL_TR_GAMES_ACCOUNTS_CALLBACK_DATA;
        }

        for(int i = 7 * (page - 1); i < 7 * page; i++){
            if (playstationStoreGames.size() - 1 < i){
                continue;
            }
            PlaystationStoreGame playstationStoreGame1 = playstationStoreGames.get(i);

            List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

            InlineKeyboardButton gameButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.SELECT_ALL_PS_GAME_DATA_CALLBACK_DATA + " " + i + " " + country + " 6")
                    .text(playstationStoreGame1.getName())
                    .build();

            keyboardButtonsRow.add(gameButton);

            rowList.add(keyboardButtonsRow);
        }

        List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        InlineKeyboardButton searchButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SELECT_GAMES_BY_SEARCH_CALLBACK_DATA + " " + country + " 6")
                .text("\uD83D\uDD0D Поиск")
                .build();


        if (page != 1){
            InlineKeyboardButton previousButton = InlineKeyboardButton.builder()
                    .callbackData(callback + " " + (page - 1))
                    .text("стр. " + (page - 1) + " из " + playstationStoreGames.size() / 7 + " ⏪")
                    .build();

            keyboardButtonsRow.add(previousButton);
        }

        keyboardButtonsRow.add(searchButton);

        if (playstationStoreGames.size() > 7 * page){
            InlineKeyboardButton nextPageButton = InlineKeyboardButton.builder()
                    .callbackData(callback + " " + (page + 1))
                    .text("стр. " + (page + 1) + " из " + playstationStoreGames.size() / 7 + " ⏩")
                    .build();

            keyboardButtonsRow.add(nextPageButton);
        }

//        InlineKeyboardButton nextPageButton = InlineKeyboardButton.builder()
//                .callbackData(callback + " " + (page + 1))
//                .text("стр. " + (page + 1) + " из " + playstationStoreGames.size() / 7 + " ⏩")
//                .build();

        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .callbackData(country.equals("ua") ? BackCallback.BACK_TO_UA_SHOP_CALLBACK_DATA : BackCallback.BACK_TO_TR_SHOP_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        //keyboardButtonsRow.add(nextPageButton);

        keyboardButtonsRow1.add(backButton);

        rowList.add(keyboardButtonsRow);
        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("<b>Вы выбрали категорию \"Все\"</b>")
                .messageId(messageId)
                .chatId(chatId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void editPsMessage(long chatId, int messageId, int page, int psVersion, String country){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        List<PlaystationStoreGame> playstationStoreGames = new ArrayList<>();

        String callback = null;

        if (psVersion == 4){
            if (country.equals("ua")){
                playstationStoreGames = AllGamesSingleton.getInstance().getAllUaPlaystationFourGames();

                callback = UserCallback.SELECT_PS4_UA_GAMES_ACCOUNTS_CALLBACK_DATA;
            } else if (country.equals("tr")){
                playstationStoreGames = AllGamesSingleton.getInstance().getAllTrPlaystationFourGames();

                callback = UserCallback.SELECT_PS4_TR_GAMES_ACCOUNTS_CALLBACK_DATA;
            }

        } else if (psVersion == 5){
            if (country.equals("ua")){
                playstationStoreGames = AllGamesSingleton.getInstance().getAllUaPlaystationFiveGames();

                callback = UserCallback.SELECT_PS5_UA_GAMES_ACCOUNTS_CALLBACK_DATA;
            } else if (country.equals("tr")){
                playstationStoreGames = AllGamesSingleton.getInstance().getAllTrPlaystationFiveGames();

                callback = UserCallback.SELECT_PS5_TR_GAMES_ACCOUNTS_CALLBACK_DATA;
            }
        }

        for(int i = 7 * (page - 1); i < 7 * page; i++){
            if (playstationStoreGames.size() - 1 < i){
                continue;
            }
            PlaystationStoreGame playstationStoreGame1 = playstationStoreGames.get(i);

            List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

            InlineKeyboardButton gameButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.SELECT_ALL_PS_GAME_DATA_CALLBACK_DATA + " " + i + " " + country + " " + psVersion)
                    .text(playstationStoreGame1.getName())
                    .build();


            keyboardButtonsRow.add(gameButton);

            rowList.add(keyboardButtonsRow);
        }

        List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        InlineKeyboardButton searchButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SELECT_GAMES_BY_SEARCH_CALLBACK_DATA + " " + country + " " + psVersion)
                .text("\uD83D\uDD0D Поиск")
                .build();


        if (page != 1){
            InlineKeyboardButton previousButton = InlineKeyboardButton.builder()
                    .callbackData(callback + " " + (page - 1))
                    .text("стр. " + (page - 1) + " из " + playstationStoreGames.size() / 7 + " ⏪")
                    .build();

            keyboardButtonsRow.add(previousButton);
        }

        keyboardButtonsRow.add(searchButton);

        if (playstationStoreGames.size() > 7 * page){
            InlineKeyboardButton nextPageButton = InlineKeyboardButton.builder()
                    .callbackData(callback + " " + (page + 1))
                    .text("стр. " + (page + 1) + " из " + playstationStoreGames.size() / 7 + " ⏩")
                    .build();

            keyboardButtonsRow.add(nextPageButton);
        }

        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .callbackData(country.equals("ua") ? BackCallback.BACK_TO_UA_SHOP_CALLBACK_DATA : BackCallback.BACK_TO_TR_SHOP_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        keyboardButtonsRow1.add(backButton);

        rowList.add(keyboardButtonsRow);
        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("<b>Вы выбрали категорию \"PS" + psVersion + "\"</b>")
                .messageId(messageId)
                .chatId(chatId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void editFilteredPsMessage(long chatId, int messageId, int page, int psVersion, String country, String text,
                                       List<PlaystationStoreGame> filteredPlaystationStoreGames){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();


        String callback = null;

        for(int i = 7 * (page - 1); i < 7 * page; i++){
            if (filteredPlaystationStoreGames.size() - 1 < i){
                continue;
            }
            PlaystationStoreGame playstationStoreGame1 = filteredPlaystationStoreGames.get(i);

            List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

            InlineKeyboardButton gameButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.SELECT_FILTERED_ALL_PS_GAME_DATA_CALLBACK_DATA + " " + i + " " + country + " " + psVersion)
                    .text(playstationStoreGame1.getName())
                    .build();


            keyboardButtonsRow.add(gameButton);

            rowList.add(keyboardButtonsRow);
        }

        List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();


        if (page != 1){
            InlineKeyboardButton previousButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.SELECT_FILTERED_GAMES_CALLBACK_DATA + " "  + page + " " + psVersion + " " + country + " -")
                    .text("стр. " + (page - 1) + " из " + filteredPlaystationStoreGames.size() / 7 + " ⏪")
                    .build();

            keyboardButtonsRow.add(previousButton);
        }

        if (filteredPlaystationStoreGames.size() > 7 * page){
            InlineKeyboardButton nextPageButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.SELECT_FILTERED_GAMES_CALLBACK_DATA + " "  + page + " " + psVersion + " " + country + " +")
                    .text("стр. " + (page + 1) + " из " + filteredPlaystationStoreGames.size() / 7 + " ⏩")
                    .build();

            keyboardButtonsRow.add(nextPageButton);
        }

        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .callbackData(country.equals("ua") ? BackCallback.BACK_TO_UA_SHOP_CALLBACK_DATA : BackCallback.BACK_TO_TR_SHOP_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        keyboardButtonsRow1.add(backButton);

        rowList.add(keyboardButtonsRow);
        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText message = EditMessageText.builder()
                .text("<b>Запрос:</b> " + text)
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void sendFilteredPsMessage(long chatId, int page, int psVersion, String country, String text,
                                       List<PlaystationStoreGame> filteredPlaystationStoreGames){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        String callback = null;

        for(int i = 7 * (page - 1); i < 7 * page; i++){
            if (filteredPlaystationStoreGames.size() - 1 < i){
                continue;
            }
            PlaystationStoreGame playstationStoreGame1 = filteredPlaystationStoreGames.get(i);

            List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

            InlineKeyboardButton gameButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.SELECT_FILTERED_ALL_PS_GAME_DATA_CALLBACK_DATA + " " + i + " " + country + " " + psVersion)
                    .text(playstationStoreGame1.getName())
                    .build();


            keyboardButtonsRow.add(gameButton);

            rowList.add(keyboardButtonsRow);
        }

        List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        if (page != 1){
            InlineKeyboardButton previousButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.SELECT_FILTERED_GAMES_CALLBACK_DATA + " "  + 1 + " " + psVersion + " " + country + " -")
                    .text("стр. " + (1 - 1) + " из " + filteredPlaystationStoreGames.size() / 7 + " ⏪")
                    .build();

            keyboardButtonsRow.add(previousButton);
        }

        if (filteredPlaystationStoreGames.size() > 7 * 1){
            InlineKeyboardButton nextPageButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.SELECT_FILTERED_GAMES_CALLBACK_DATA + " "  + 1 + " " + psVersion + " " + country + " +")
                    .text("стр. " + (1 + 1) + " из " + filteredPlaystationStoreGames.size() / 7 + " ⏩")
                    .build();

            keyboardButtonsRow.add(nextPageButton);
        }

        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .callbackData(country.equals("ua") ? BackCallback.BACK_TO_UA_SHOP_CALLBACK_DATA : BackCallback.BACK_TO_TR_SHOP_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        keyboardButtonsRow1.add(backButton);

        rowList.add(keyboardButtonsRow);
        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("<b>Запрос:</b> " + text)
                .chatId (chatId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }
    private void editShopMessage(long chatId, int messageId, String country){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        String allGamesCallback = null;
        String ps4GamesCallback = null;
        String ps5GamesCallback = null;

        String flag = null;

        if (country.equals("ua")){
            allGamesCallback = UserCallback.SELECT_ALL_UA_GAMES_ACCOUNTS_CALLBACK_DATA;
            ps4GamesCallback = UserCallback.SELECT_PS4_UA_GAMES_ACCOUNTS_CALLBACK_DATA;
            ps5GamesCallback = UserCallback.SELECT_PS5_UA_GAMES_ACCOUNTS_CALLBACK_DATA;

            flag = "\uD83C\uDDFA\uD83C\uDDE6";
        } else if (country.equals("tr")){
            allGamesCallback = UserCallback.SELECT_ALL_TR_GAMES_ACCOUNTS_CALLBACK_DATA;
            ps4GamesCallback = UserCallback.SELECT_PS4_TR_GAMES_ACCOUNTS_CALLBACK_DATA;
            ps5GamesCallback = UserCallback.SELECT_PS5_TR_GAMES_ACCOUNTS_CALLBACK_DATA;

            flag = "\uD83C\uDDF9\uD83C\uDDF7";
        }

        InlineKeyboardButton allGamesButton = InlineKeyboardButton.builder()
                .callbackData(allGamesCallback + " 1")
                .text("Все игры")
                .build();

        InlineKeyboardButton ps4Button = InlineKeyboardButton.builder()
                .callbackData(ps4GamesCallback + " 1")
                .text("Игры для PS4")
                .build();

        InlineKeyboardButton ps5Button = InlineKeyboardButton.builder()
                .callbackData(ps5GamesCallback + " 1")
                .text("Игры для PS5")
                .build();

        InlineKeyboardButton backToRegionButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_REGION_CALLBACK_DATA)
                .text("◀\uFE0F Назад к регионам")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow3 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow4 = new ArrayList<>();

        keyboardButtonsRow1.add(allGamesButton);
        keyboardButtonsRow2.add(ps4Button);
        keyboardButtonsRow3.add(ps5Button);
        keyboardButtonsRow4.add(backToRegionButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);
        rowList.add(keyboardButtonsRow3);
        rowList.add(keyboardButtonsRow4);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessage = EditMessageText.builder()
                .text(flag + " <b>Выберите нужную Вам категорию, чтобы посмотреть список игр в</b> " + flag)
                .chatId(chatId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .messageId(messageId)
                .build();

        try {
            execute(editMessage);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    public void sendPlayStoreGameData(long chatId, PlaystationStoreGame playstationStoreGame, String country){
        String imageURL = playstationStoreGame.getImageUrl();
        double cost = 0;
        boolean isPsVersionFive = false;
        boolean isPsVersionFour = false;

        switch (playstationStoreGame.getPsVersion()) {
            case "PS4" -> isPsVersionFour = true;
            case "PS5" -> isPsVersionFive = true;
            case "PS4, PS5" -> {
                isPsVersionFour = true;
                isPsVersionFive = true;
            }
        }

        double establishedCoefficient = 0;

        cost += playstationStoreGame.getCost();

        if (cost >= 500){
            establishedCoefficient = coefficientRepository.findCoefficientByRangeAndCountry("500-*", country);
        } else if (cost <= 500 && cost >= 100){
            establishedCoefficient = coefficientRepository.findCoefficientByRangeAndCountry("100-500", country);
        } else if (cost <= 100){
            establishedCoefficient = coefficientRepository.findCoefficientByRangeAndCountry("0-100", country);
        }

        File file = null;

        try {
            file = UrlFileDownloader.downloadTempFile(imageURL, "tempImage", ".jpg");
        } catch (Exception e){
            log.warn(e.getMessage());
        }


        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToSetButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_DELETE_CALLBACK_DATA)
                .text("◀\uFE0F Назад к списку")
                .build();

        InlineKeyboardButton cartButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.ADD_GAME_TO_CART_CALLBACK_DATA + " " + country + " " + playstationStoreGame.getPsVersion())
                .text("✅ Добавить в корзину")
                .build();


        List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

        keyboardButtonsRow.add(backToSetButton);
        keyboardButtonsRow.add(cartButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendPhoto photoMessage = SendPhoto.builder()
                .caption("\uD83C\uDFAE <b>Игра:</b> "+ playstationStoreGame.getName() + " \uD83C\uDFAE \n"
                + "Для " + (country.equals("ua") ? "\uD83C\uDDFA\uD83C\uDDE6 (украинского)" : "\uD83C\uDDF9\uD83C\uDDF7 (турецкого)") + " аккаунта\n\n"
                + "❓ <b>Информация по игре</b> ❓\n"
                + "Для PS4: " + (isPsVersionFour ? "✅" : "❌") + "\n"
                + "Для PS5: " + (isPsVersionFive ? "✅" : "❌") + "\n\n"
//                + "<b>СКИДКА: -50%</b>\n"
//                + "<b>ЗАКАНЧИВАЕТСЯ: 2024-01-05 23:59:00</b>\n\n"
                + "<b>Цена:</b> " + (Math.round((cost * establishedCoefficient) * 100.0) / 100.0) + " <b>рублей</b>")
                .photo(new InputFile(file))
                .replyMarkup(inlineKeyboardMarkup)
                .chatId(chatId)
                .parseMode("html")
                .build();

        try {
            execute(photoMessage);
            file.delete();
        }catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void editCartMessage(long chatId, int messageId){
        StringBuilder gamesText = new StringBuilder();
        double sumPrice = 0;

        List<PlaystationStoreGame> playstationStoreGameList = idCartMap.get(chatId);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();


        if (playstationStoreGameList == null){
            playstationStoreGameList = new ArrayList<>();
        }

        Iterable<Coefficient> coefficientIterable = coefficientRepository.findAll();

        for (int i = 0; i < playstationStoreGameList.size(); i++){
            PlaystationStoreGame playstationStoreGame = playstationStoreGameList.get(i);

            AtomicReference<Double> establishedCoefficient = new AtomicReference<>((double) 0);

            if (playstationStoreGame.getCost() >= 500){
                coefficientIterable.forEach(c -> {
                    if (c.getRange().equals("500-*") && c.getCountry().equals(playstationStoreGame.getCountry())){
                        establishedCoefficient.set(c.getCoefficient());
                    }
                });
            } else if (playstationStoreGame.getCost() <= 500 && playstationStoreGame.getCost() >= 100){
                coefficientIterable.forEach(c -> {
                    if (c.getRange().equals("100-500") && c.getCountry().equals(playstationStoreGame.getCountry())){
                        establishedCoefficient.set(c.getCoefficient());
                    }
                });
            } else if (playstationStoreGame.getCost() <= 100){
                coefficientIterable.forEach(c -> {
                    if (c.getRange().equals("0-100") && c.getCountry().equals(playstationStoreGame.getCountry())){
                        establishedCoefficient.set(c.getCoefficient());
                    }
                });
            }


            sumPrice += playstationStoreGame.getCost() * establishedCoefficient.get();

            gamesText.append((playstationStoreGame.getCountry().equals("ua") ? "\uD83C\uDDFA\uD83C\uDDE6" : "\uD83C\uDDF9\uD83C\uDDF7"))
                    .append(" ").append(playstationStoreGame.getName()).append("\n");

            InlineKeyboardButton removeGameButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.REMOVE_GAME_FROM_CART_CALLBACK_DATA + " " + i)
                    .text("❌ Удалить " + playstationStoreGame.getName())
                    .build();

            List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

            keyboardButtonsRow.add(removeGameButton);

            rowList.add(keyboardButtonsRow);
        }

        sumPrice = Math.round(sumPrice * 100.0) / 100.0;

        if (playstationStoreGameList.size() != 0){
            InlineKeyboardButton removeGameButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.REMOVE_GAME_FROM_CART_CALLBACK_DATA + " *")
                    .text("\uD83D\uDEAB Удалить все")
                    .build();

            List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

            keyboardButtonsRow.add(removeGameButton);

            rowList.add(keyboardButtonsRow);
        }

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessage = EditMessageText.builder()
                .text("<b>Игр в корзине на сумму:</b> " + sumPrice + " ₽\n"
                        + "<b>Список игр добавленных в корзину:</b>\n"
                        + "-----------\n"
                        + gamesText)
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessage);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }
    private void sendCartMessage(long chatId) {
            StringBuilder gamesText = new StringBuilder();
            double sumPrice = 0;

            List<PlaystationStoreGame> playstationStoreGameList = idCartMap.get(chatId);

            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

            List<List<InlineKeyboardButton>> rowList = new ArrayList<>();


            if (playstationStoreGameList == null) {
                playstationStoreGameList = new ArrayList<>();
            }


            Iterable<Coefficient> coefficientIterable = coefficientRepository.findAll();

            for (int i = 0; i < playstationStoreGameList.size(); i++){
                PlaystationStoreGame playstationStoreGame = playstationStoreGameList.get(i);

                AtomicReference<Double> establishedCoefficient = new AtomicReference<>((double) 0);

                if (playstationStoreGame.getCost() >= 500){
                    coefficientIterable.forEach(c -> {
                        if (c.getRange().equals("500-*") && c.getCountry().equals(playstationStoreGame.getCountry())){
                            establishedCoefficient.set(c.getCoefficient());
                        }
                    });
                } else if (playstationStoreGame.getCost() <= 500 && playstationStoreGame.getCost() >= 100){
                    coefficientIterable.forEach(c -> {
                        if (c.getRange().equals("100-500") && c.getCountry().equals(playstationStoreGame.getCountry())){
                            establishedCoefficient.set(c.getCoefficient());
                        }
                    });
                } else if (playstationStoreGame.getCost() <= 100){
                    coefficientIterable.forEach(c -> {
                        if (c.getRange().equals("0-100") && c.getCountry().equals(playstationStoreGame.getCountry())){
                            establishedCoefficient.set(c.getCoefficient());
                        }
                    });
                }


                sumPrice += playstationStoreGame.getCost() * establishedCoefficient.get();

                gamesText.append((playstationStoreGame.getCountry().equals("ua") ? "\uD83C\uDDFA\uD83C\uDDE6" : "\uD83C\uDDF9\uD83C\uDDF7"))
                        .append(" ").append(playstationStoreGame.getName()).append("\n");

                InlineKeyboardButton removeGameButton = InlineKeyboardButton.builder()
                        .callbackData(UserCallback.REMOVE_GAME_FROM_CART_CALLBACK_DATA + " " + i)
                        .text("❌ Удалить " + playstationStoreGame.getName())
                        .build();

                List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

                keyboardButtonsRow.add(removeGameButton);

                rowList.add(keyboardButtonsRow);
            }

            sumPrice = Math.round(sumPrice * 100.0) / 100.0;

            if (playstationStoreGameList.size() != 0) {
                InlineKeyboardButton removeGameButton = InlineKeyboardButton.builder()
                        .callbackData(UserCallback.REMOVE_GAME_FROM_CART_CALLBACK_DATA + " *")
                        .text("\uD83D\uDEAB Удалить все")
                        .build();

                List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();

                keyboardButtonsRow.add(removeGameButton);

                rowList.add(keyboardButtonsRow);
            }

            inlineKeyboardMarkup.setKeyboard(rowList);

            SendMessage message = SendMessage.builder()
                    .text("<b>Игр в корзине на сумму:</b> " + sumPrice + " ₽\n"
                            + "<b>Список игр добавленных в корзину:</b>\n"
                            + "-----------\n"
                            + gamesText)
                    .chatId(chatId)
                    .replyMarkup(inlineKeyboardMarkup)
                    .parseMode("html")
                    .build();

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.warn(e.getMessage());
            }
    }

    private void sendAdminMessage(long chatId){
        if (adminRepository.findByTelegramUserId(chatId) == null){
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton dispatchButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_DISPATCH_CALLBACK_DATA)
                .text("\uD83D\uDCE3 Рассылка")
                .build();

        InlineKeyboardButton coefficientButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_CHANGE_COEFFICIENT_CALLBACK_DATA)
                .text("\uD83D\uDD04 Изменить курсы")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(dispatchButton);
        keyboardButtonsRow2.add(coefficientButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("\uD83D\uDED1 <b>Админ-панель</b>\n\n"
                + "Чем больше сила, тем больше и ответственность!")
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void editAdminMessage(long chatId, int messageId){
        if (adminRepository.findByTelegramUserId(chatId) == null){
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton dispatchButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_DISPATCH_CALLBACK_DATA)
                .text("\uD83D\uDCE3 Рассылка")
                .build();

        InlineKeyboardButton coefficientButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_CHANGE_COEFFICIENT_CALLBACK_DATA)
                .text("\uD83D\uDD04 Изменить коэффициенты")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(dispatchButton);
        keyboardButtonsRow2.add(coefficientButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText message = EditMessageText.builder()
                .text("\uD83D\uDED1 <b>Админ-панель</b>\n\n"
                        + "Чем больше сила, тем больше и ответственность!")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void editCoefficientMessage(long chatId, int messageId){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton zeroOneHundredUaButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_CHANGE_COEFFICIENT_VALUE_CALLBACK_DATA + " 0-100 ua")
                .text("\uD83C\uDDFA\uD83C\uDDE6 0-100")
                .build();
        InlineKeyboardButton oneHundredFiveHundredUaButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_CHANGE_COEFFICIENT_VALUE_CALLBACK_DATA + " 100-500 ua")
                .text("\uD83C\uDDFA\uD83C\uDDE6 100-500")
                .build();
        InlineKeyboardButton fiveHundredInfinityUaButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_CHANGE_COEFFICIENT_VALUE_CALLBACK_DATA + " 500-* ua")
                .text("\uD83C\uDDFA\uD83C\uDDE6 500-∞")
                .build();
        InlineKeyboardButton zeroOneHundredTrButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_CHANGE_COEFFICIENT_VALUE_CALLBACK_DATA + " 0-100 tr")
                .text("\uD83C\uDDF9\uD83C\uDDF7 0-100")
                .build();
        InlineKeyboardButton oneHundredFiveHundredTrButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_CHANGE_COEFFICIENT_VALUE_CALLBACK_DATA + " 100-500 tr")
                .text("\uD83C\uDDF9\uD83C\uDDF7 100-500")
                .build();
        InlineKeyboardButton fiveHundredInfinityTrButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_CHANGE_COEFFICIENT_VALUE_CALLBACK_DATA + " 500-* tr")
                .text("\uD83C\uDDF9\uD83C\uDDF7 500-∞")
                .build();

        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow.add(zeroOneHundredUaButton);
        keyboardButtonsRow.add(oneHundredFiveHundredUaButton);
        keyboardButtonsRow.add(fiveHundredInfinityUaButton);

        keyboardButtonsRow1.add(zeroOneHundredTrButton);
        keyboardButtonsRow1.add(oneHundredFiveHundredTrButton);
        keyboardButtonsRow1.add(fiveHundredInfinityTrButton);

        keyboardButtonsRow2.add(backButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow);
        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessage = EditMessageText.builder()
                .text("\uD83D\uDD04 <b>Выберите диапазон для каждой валюты</b>")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessage);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void deleteMessage(long chatId, int messageId){
        DeleteMessage deleteMessage = DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build();

        try {
            execute(deleteMessage);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }
}
