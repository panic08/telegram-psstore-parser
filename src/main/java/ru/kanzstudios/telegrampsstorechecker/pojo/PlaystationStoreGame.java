package ru.kanzstudios.telegrampsstorechecker.pojo;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@EqualsAndHashCode
public class PlaystationStoreGame{
    private String name;
    private double cost;
    private String country;
    private String url;
    private String imageUrl;
    private String psVersion;
}
