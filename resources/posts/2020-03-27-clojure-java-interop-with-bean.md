Title: Clojure: java interop with bean

One of the great things with Clojure is it has fantastic java interop. In this article we explore the `bean` function and how it makes working with java object more idiomatic.

### Some Java to interop with

First we import a java library. For this example we will use  [googlei18n/libphonenumber](https://github.com/googlei18n/libphonenumber/tree/master/java/libphonenumber/src/com/google/i18n/phonenumbers) a google library for processing numbers. We want to use it to split a phone number into its different parts (country code and national number):

```Clojure
(import [com.google.i18n.phonenumbers PhoneNumberUtil])
```

Let's parse a phone number:

```Clojure
(.parse (PhoneNumberUtil/getInstance) "+41446681800" "")

=>
#object[com.google.i18n.phonenumbers.Phonenumber$PhoneNumber 0x57762fc2 "Country Code: 41 National Number: 446681800"]
```

We get an object back. The string name of the object implies that it might contain what we are interested in (country code and national number).

### Without Bean

Lets see if the object contains a method for extracting the country code from this object:

```Clojure
(->> (.getDeclaredMethods com.google.i18n.phonenumbers.Phonenumber$PhoneNumber)
     (map #(.getName %))
     (filter #(clojure.string/includes? % "Code")))

=>
("hashCode"
 "hasPreferredDomesticCarrierCode"
 "clearCountryCode"
 "clearPreferredDomesticCarrierCode"
 "getCountryCode"
 "setCountryCode"
 "clearCountryCodeSource"
 "setPreferredDomesticCarrierCode"
 "setCountryCodeSource"
 "getPreferredDomesticCarrierCode"
 "hasCountryCodeSource"
 "getCountryCodeSource"
 "hasCountryCode")
```

And a method for extracting the national number:

```Clojure
(->> (.getDeclaredMethods com.google.i18n.phonenumbers.Phonenumber$PhoneNumber)
     (map #(.getName %))
     (filter #(clojure.string/includes? % "Number")))

=>
("hasNationalNumber"
 "hasNumberOfLeadingZeros"
 "clearNationalNumber"
 "clearNumberOfLeadingZeros"
 "setNumberOfLeadingZeros"
 "setNationalNumber"
 "getNumberOfLeadingZeros"
 "getNationalNumber")
 ```

We can now use `.getCountryCode` and `.getNationalNumber` to build a function that splits the phone number into its different parts:

```Clojure
(let [number-obj     (.parse (PhoneNumberUtil/getInstance) "+41446681800" "")
      countryCode    (.getCountryCode number-obj)
      nationalNumber (.getNationalNumber number-obj)]
  {:phone/full-number      (str "+" countryCode nationalNumber)
   :phone/prefix           (str "+" countryCode)
   :phone/number-no-prefix (str nationalNumber)})

=>
#:phone{:full-number      "+41446681800",
        :prefix           "+41",
        :number-no-prefix "446681800"}
```

### With Bean

The `bean` function converts a java object into a clojure map:

```Clojure
(bean (.parse (PhoneNumberUtil/getInstance) "+41446681800" ""))

=>
{:numberOfLeadingZeros         1,
 :preferredDomesticCarrierCode "",
 :extension                    "",
 :italianLeadingZero           false,
 :class                        com.google.i18n.phonenumbers.Phonenumber$PhoneNumber,
 :countryCodeSource
 #object[com.google.i18n.phonenumbers.Phonenumber$PhoneNumber$CountryCodeSource 0x2724e628 "UNSPECIFIED"],
 :nationalNumber               446681800,
 :rawInput                     "",
 :countryCode                  41}
```

This allows us to use all the tools of the language (in this case destructuring) to extract the data we care about:

```Clojure
(let [{:keys [countryCode nationalNumber]}
      (bean (.parse (PhoneNumberUtil/getInstance) "+41446681800" ""))]
  {:phone/full-number      (str "+" countryCode nationalNumber)
   :phone/prefix           (str "+" countryCode)
   :phone/number-no-prefix (str nationalNumber)})

=>
#:phone{:full-number      "+41446681800",
        :prefix           "+41",
        :number-no-prefix "446681800"}
```

Much simpler!

That covers using the `bean` function and how it makes working with java object more idiomatic.
