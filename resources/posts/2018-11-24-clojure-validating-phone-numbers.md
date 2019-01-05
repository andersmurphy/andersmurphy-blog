Title: Clojure: validating phone numbers

Sometimes you need to validate phone numbers, [googlei18n/libphonenumber](https://github.com/googlei18n/libphonenumber/tree/master/java/libphonenumber/src/com/google/i18n/phonenumbers) is a Java library for doing just that (and more). Thanks to Clojure's great java interop using this library in your project is straightforward.

First, we add the `com.googlecode.libphonenumber` dependency to the leiningen `project.clj` file.

```clojure
(defproject phone-number-validation "0.1.0-SNAPSHOT"
  :dependencies [[com.googlecode.libphonenumber/libphonenumber "8.9.9"]
```

Then we import the `PhoneNumberUtil` class and use the `.parse` method which takes: an instance of `PhoneNumberUtil`, a phone number string, and a default region string (which in this case we have just left empty). It returns a `PhoneNumber` which we then passe into the `.isValidNumber` method for validation.

*Note: According to the source code `PhoneNumberUtil/getInstance` implements the singleton pattern. So, calling it multiple times only results in one instance being created.*

```clojure
(ns core
  (:import [com.google.i18n.phonenumbers PhoneNumberUtil]))

(defn valid-number? [number]
  (->> (.parse (PhoneNumberUtil/getInstance) number "")
       (.isValidNumber (PhoneNumberUtil/getInstance))))

=> (valid-number? "+41446681800")

true

=> (valid-number? "+41543")

false
```

The above works well enough so far. But, what if we try something more "obviously wrong"?

```clojure
=> (valid-number? "+41")

Execution error (NumberParseException) at com.google.i18n.phonenumbers.PhoneNumberUtil/parseHelper (PhoneNumberUtil.java:3151).
The string supplied did not seem to be a phone number.
```

We get an exception. If we look at the `.parse` method's source code this is expected behaviour `throws NumberParseException`. We can make our `valid-number?` function handle this exception with `try` and  `catch`.

```clojure
(ns core
  (:import [com.google.i18n.phonenumbers PhoneNumberUtil]
           [com.google.i18n.phonenumbers NumberParseException]))

(defn valid-number? [number]
  (try
    (->> (.parse (PhoneNumberUtil/getInstance) number "")
         (.isValidNumber (PhoneNumberUtil/getInstance)))
    (catch NumberParseException e false)))

=> (valid-number? "+41446681800")

true

=> (valid-number? "+41543")

false

=> (valid-number? "+41")

false

=> (valid-number? "rubbish")

false
```

Great our function now handles any string we throw at it! That wraps up our short introduction to [googlei18n/libphonenumber](https://github.com/googlei18n/libphonenumber/tree/master/java/libphonenumber/src/com/google/i18n/phonenumbers).
