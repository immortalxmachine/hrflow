package com.hrflow.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/** A public holiday in a given country. Used to skip non-working days. */
@Entity
@Table(name = "public_holidays")
public class PublicHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Country country;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String name;

    protected PublicHoliday() { }

    public Long getId() { return id; }
    public Country getCountry() { return country; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getName() { return name; }
}
