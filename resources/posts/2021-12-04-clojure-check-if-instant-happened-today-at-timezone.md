Title: Clojure: check if instant happened today at timezone

Say you are making a digital advent calendar app. You want users to get a special reward on the days that they open your app. But only once per day and only on the days they open the app. This sounds straight forward. What about time zones? What about users who open the app on the 1st of December at 23:55 and then on the 2nd of December at 00:03? Time is tricky.

We will be using `java.time` as it comes with Clojure out of the box. Unfortunately, there are no built in reader literals for `java.time.Instant` ([checkout this post for how to add them](https://andersmurphy.com/2019/08/03/clojure-using-java-time-with-jdbc.html#reader_literals)). Throughout this example we will use the code below to create a `java.time.Instant` from a `java.util.Date` literal:

```Clojure
(.toInstant #inst "2021-12-04T00:00:00Z")
```

First lets open some javadoc from the repl. The `javadoc` function opens your default browser and points it to the relevant documentation:

```Clojure
(clojure.java.javadoc/javadoc java.time.Instant)
(clojure.java.javadoc/javadoc java.time.ZoneId)
```

Looking over the `java.time` documentation we can piece together the following function:

```Clojure
(defn instant->localDate-at-timezone [instant tz]
  (->> (java.time.ZoneId/of tz)
       (.atZone instant)
       (.toLocalDate)))
```

This seems to work:

```Clojure
(instant->localDate-at-timezone
 (.toInstant #inst "2021-12-04T00:00:00Z") "UTC")

=>
#object[java.time.LocalDate 0x73ad4ecc "2021-12-04"]
```

Whenever you I use Java interop I like to make sure I'm not inadvertently doing any reflection. Reflection is expensive and can easily be avoided in most cases by adding some type hints. Thankfully, Clojure can warn us on reflection use by setting `*warn-on-reflection*` to `true`:

```Clojure
(set! *warn-on-reflection* true)
```

If we evaluate the `instant->localDate-at-timezone` definition again we will get the following warning:

```Clojure
Reflection warning ...
- call to method atZone can't be resolved
(target class is unknown)
```
This can easily be fixed by adding the `^java.time.Instant` type hint:

```Clojure
(defn instant->localDate-at-timezone [instant tz]
  (->> (java.time.ZoneId/of tz)
       (.atZone ^java.time.Instant instant)
       (.toLocalDate)))
```

You won't see any more warnings. So let's set `*warn-on-reflection*` back to `nil`:

```Clojure
(set! *warn-on-reflection* nil)
```

Let's find some more time zones to test this function on. I can never remember the exact ids, but that's fine as we can just ask Java:

```Clojure
(->> (java.time.ZoneId/getAvailableZoneIds)
     (filter #(clojure.string/includes? % "Pacific")))

=>
("Canada/Pacific"
 "Pacific/Apia"
 "Pacific/Auckland"
 "Pacific/Bougainville"
 "Pacific/Chatham"
 "Pacific/Chuuk"
 "Pacific/Easter"
 "Pacific/Efate"
 "Pacific/Enderbury"
 "Pacific/Fakaofo"
 "Pacific/Fiji"
 ...)
```

Testing the function with  `"Pacific/Fiji"` gives us the correct date (2021-12-05) rather than the 2021-12-04):

```Clojure
(instant->localDate-at-timezone
 (.toInstant #inst "2021-12-04T00:00:00Z") "Pacific/Fiji")

=>
[java.time.LocalDate 0x9fec931 "2021-12-05"]
```

We wrap this up by writing a function to check a UTC instant and timezone against the date in that current time zone:

```Clojure
(defn instant-today-at-tz? [instant tz]
  (= (->> (java.time.ZoneId/of tz)
          (.atZone ^java.time.Instant instant)
          (.toLocalDate))
     (java.time.LocalDate/now
      (java.time.ZoneId/of tz))))
```

One last test:

```Clojure
(instant-today-at-tz?
 (.toInstant #inst "2021-12-04T00:00:00Z")
 "Pacific/Fiji")

=>
false

(instant-today-at-tz?
 (.toInstant #inst "2021-12-04T23:00:00Z")
 "Pacific/Fiji")

=>
true

(instant-today-at-tz?
 (.toInstant #inst "2021-12-05T00:00:00Z")
 "Pacific/Fiji")

=>
true
```

Everything is working as expected. We can now ensure our users get their daily digital advent content regardless of where they are in the world.
