package ru.kanzstudios.telegrampsstorechecker.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CustomPair <T, L>{
    private T tuple1;
    private L tuple2;
}
