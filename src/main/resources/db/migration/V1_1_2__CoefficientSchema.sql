CREATE TABLE IF NOT EXISTS coefficients_table(
    id SERIAL PRIMARY KEY,
    range VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    coefficient DOUBLE PRECISION NOT NULL
);

INSERT INTO coefficients_table (range, country, coefficient) VALUES ('0-100', 'ua', 2.43);
INSERT INTO coefficients_table (range, country, coefficient) VALUES ('100-500', 'ua', 2.43);
INSERT INTO coefficients_table (range, country, coefficient) VALUES ('500-*', 'ua', 2.43);

INSERT INTO coefficients_table (range, country, coefficient) VALUES ('0-100', 'tr', 3.13);
INSERT INTO coefficients_table (range, country, coefficient) VALUES ('100-500', 'tr', 3.13);
INSERT INTO coefficients_table (range, country, coefficient) VALUES ('500-*', 'tr', 3.13);