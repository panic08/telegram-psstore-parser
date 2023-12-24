package ru.kanzstudios.telegrampsstorechecker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table(name = "coefficients_table")
public class Coefficient {
    @Id
    private Long id;
    private String range;
    private String country;
    private Double coefficient;
}
