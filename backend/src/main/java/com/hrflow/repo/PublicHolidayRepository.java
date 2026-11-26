package com.hrflow.repo;

import com.hrflow.domain.Country;
import com.hrflow.domain.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    /** Holidays for a country within an inclusive date range. */
    List<PublicHoliday> findByCountryAndHolidayDateBetween(Country country, LocalDate from, LocalDate to);
}
