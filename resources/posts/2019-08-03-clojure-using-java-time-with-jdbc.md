Title: Clojure: using java.time with clojure.java.jdbc

Java 8 introduced `java.time` to replace the existing java representations of time `java.util.Date`, `java.sql.Timestamp` etc. There were many problems with this old implementation: it wasn't thread safe, it had a clunky API and no built in representation for timezones. `java.time` is the successor to the fantastic `joda.time` project which solves all these problems. So if `java.time` fixes everything then why this article? Well, `java.sql.Timestamp` still rears its ugly head at the database layer where it is still the default representation of time in the `java.jdbc` database drivers. In this article we will cover how to automate the conversion between `java.sql.Timestamp` and `java.time` so you never have to deal with `java.sql.Timestamp` again.

## Initial set up

Start postgres.

```bash
pg_ctl -D pg start
```

Create a database called `databasename`.

```bash
createdb databasename
```

### Dependencies

Add `org.clojure/java.jdbc` and `org.postgresql/postgresql` to your dependencies.

```clojure
(defproject jdbc-java-time-example "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/java.jdbc "0.7.7"]
                 [org.postgresql/postgresql "42.2.3"]])
```

## Database connection

Start with importing all the needed dependencies, creating a database connection and a test table.

```clojure
(ns jdbc-java-time-example.core
  (:require [clojure.java.jdbc :as jdbc])
  (:import [java.sql Timestamp]
           [java.sql Date]
           [java.time.format DateTimeFormatter]
           [java.time LocalDate]
           [java.time Instant]
           [java.io FileWriter]))

(def database-connection "postgresql://localhost:5432/databasename")

(jdbc/execute!
   database-connection
   "CREATE TABLE event (pid SERIAL PRIMARY KEY, name text,
   created timestamp with time zone,
   log_date date )")
```

## Extending JDBC with java.time

Extend `jdbc/IResultsetReadColumn` to convert `java.sql.Timestamp` to `java.time.Instant` and `java.sql.Date` to `java.time.LocalDate`.

```clojure
(extend-protocol jdbc/IResultSetReadColumn
  java.sql.Timestamp
  (result-set-read-column [v _ _]
    (.toInstant v))
  java.sql.Date
  (result-set-read-column [v _ _]
    (.toLocalDate v)))
```

Extend `jdbc/ISQLValue` to convert `java.time.Instant` to `java.sql.Timestamp` and `java.time.LocalDate` to `java.sql.Date`.

```clojure
(extend-protocol jdbc/ISQLValue
  java.time.Instant
  (sql-value [v]
    (Timestamp/from v))
  java.time.LocalDate
  (sql-value [v]
    (Date/valueOf v)))
```

## Read and write time

Inserting `java.time` values works as expected.

```clojure
(jdbc/insert!
   database-connection
   :event
   {:name     "page_viewed"
    :created  (Instant/now)
    :log_date (LocalDate/now)})

=> ({:pid 1,
     :name "page_viewed",
     :created #object[java.time.Instant 0x1d43ad18 "2019-08-03T16:28:25.935Z"],
     :log_date #object[java.time.LocalDate 0x265e4f6d "2019-08-03"]})
```

Same for reading `java.time` values.

```clojure
(jdbc/query
   database-connection
   ["select * from event"])

=> ({:pid 1,
     :name "page_viewed",
     :created #object[java.time.Instant 0x1d43ad18 "2019-08-03T16:28:25.935Z"],
     :log_date #object[java.time.LocalDate 0x265e4f6d "2019-08-03"]})
```

## Reader literals

To make `java.time` values easier to work with we can add support for reader literals. For Clojure to be able to print the literals define the following methods.

```clojure
(defmethod print-method java.time.Instant
  [inst out]
  (.write out (str "#time/inst \"" (.toString inst) "\"") ))

(defmethod print-dup java.time.Instant
  [inst out]
  (.write out (str "#time/inst \"" (.toString inst) "\"") ))

(defmethod print-method LocalDate
  [^LocalDate date ^FileWriter out]
  (.write out (str "#time/ld \"" (.toString date) "\"")))

(defmethod print-dup LocalDate
  [^LocalDate date ^FileWriter out]
  (.write out (str "#time/ld \"" (.toString date) "\"")))
```

To allow Clojure to read these literals first create two functions for passing date and time.

```clojure
(defn parse-date [string]
  (LocalDate/parse string))

(defn parse-time [string]
  (and string (-> (.parse (DateTimeFormatter/ISO_INSTANT) string)
                  Instant/from)))
```

Then create a file called `data_readers.clj` in your project `resources` folder (this will add it to the class path).

```clojure
{time/inst jdbc-java-time-example.core/parse-time
 time/ld   jdbc-java-time-example.core/parse-date}
```

Writing literals works as expected.

```clojure
(jdbc/insert!
   database-connection
   :event
   {:name     "page_viewed"
    :created  #time/inst "2019-08-03T16:28:25.935Z"
    :log_date #time/ld "2019-08-03"})

=> ({:pid 1,
     :name "page_viewed",
     :created #time/inst "2019-08-03T16:28:25.935Z",
     :log_date #time/ld "2019-08-03"})
```

Reading prints literals.

```clojure
(jdbc/query
   database-connection
   ["select * from event"])

=> ({:pid 1,
     :name "page_viewed",
     :created #time/inst "2019-08-03T16:28:25.935Z",
     :log_date #time/ld "2019-08-03"})
```

This concludes this guide to using `java.time` with `clojure.java.jdbc` as well as a brief introduction to reader literals. The full example project can be found [here](https://github.com/andersmurphy/clj-cookbook/tree/master/database-type-conversion/jdbc-java-time-example).
