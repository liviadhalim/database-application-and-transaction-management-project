-- add all your SQL setup statements here. 

-- You can assume that the following base table has been created with data loaded for you when we test your submission 
-- (you still need to create and populate it in your instance however),
-- although you are free to insert extra ALTER COLUMN ... statements to change the column 
-- names / types if you like.

-- CREATE TABLE FLIGHTS
-- (
--  fid int NOT NULL PRIMARY KEY,
--  year int,
--  month_id int,
--  day_of_month int,
--  day_of_week_id int,
--  carrier_id varchar(3),
--  flight_num int,
--  origin_city varchar(34),
--  origin_state varchar(47),
--  dest_city varchar(34),
--  dest_state varchar(46),
--  departure_delay double precision,
--  taxi_out double precision,
--  arrival_delay double precision,
--  canceled int,
--  actual_time double precision,
--  distance double precision,
--  capacity int,
--  price double precision
--)

CREATE TABLE USERS
(
  username varchar(30) NOT NULL PRIMARY KEY,
  password varchar(30),
  balance double precision
)


CREATE TABLE RESERVATIONS
(
  unique_id int NOT NULL PRIMARY KEY,
  username varchar(30) REFERENCES USERS(username),
  day_of_month int,
  fid1 int REFERENCES FLIGHTS(fid),
  fid2 int REFERENCES FLIGHTS(fid),
  price double precision,
  paid bit,
  cancelled bit
)

