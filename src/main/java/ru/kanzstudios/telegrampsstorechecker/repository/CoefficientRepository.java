package ru.kanzstudios.telegrampsstorechecker.repository;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.kanzstudios.telegrampsstorechecker.model.Coefficient;

@Repository
public interface CoefficientRepository extends CrudRepository<Coefficient, Long> {
    @Query("UPDATE coefficients_table SET coefficient = :newCoefficient WHERE range = :range AND country = :country")
    @Modifying
    void updateCoefficientByRangeAndCountry(@Param("newCoefficient") double coefficient,
                                            @Param("range") String range,
                                            @Param("country") String country);
    @Query("SELECT ct.coefficient FROM coefficients_table ct WHERE ct.range = :range AND ct.country = :country")
    double findCoefficientByRangeAndCountry(@Param("range") String range,
                            @Param("country") String country);
}
