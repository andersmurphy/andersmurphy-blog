3Title: Clojure: sending emails with SendGrid

Your business needs you to generate an automated email report containing data from your app. In this example we will use the [SendGrid](https://sendgrid.com) web API to email a `.csv` file.

### Get a SendGrid API key

First, you need to get a SendGrid API key. I'm not going to cover this in any more detail as there is plenty of [documentation](https://sendgrid.com/docs/ui/account-and-settings/api-keys/) available on their website.

Once you have your API key, you can pass it into your app as an environment variable.

### Set up the example data

We want to send some data in `.csv` format as an attachment. We are just using a list of maps in this case. However, in a real-world application, this could be data you get back from a database query. Here's the data.

```clojure
(def data
  [{:name "Bob" :age 27 :favourite-food "bagels"}
   {:name "Sarah" :age 23 :favourite-food "apples"}
   {:name "John" :age 41 :favourite-food "pasta"}])
```

Next, we need a function to convert this data into csv format.

```clojure
(defn escape-csv-value [value]
  (str "\"" value "\""))

(defn row->csv-row [row]
  (->> (map escape-csv-value row)
       (str/join ",")))

(defn ms->csv-string [ms]
  (let [columns (keys (first ms))
        headers (map name columns)
        rows    (map #(map % columns) ms)]
    (->> (into [headers] rows)
         (map row->csv-row)
         (str/join "\n"))))
```

### Sendgrid Rest API

First lets look at the SendGrid Web API [documentation](https://sendgrid.com/docs/API_Reference/Web_API_v3/Mail/index.html). From the documentation, we can see that we need to make the following request.

```
POST https://api.sendgrid.com/v3/mail/send
content-type: application/json
Authorization: Bearer YOUR_API_KEY

{
  "personalizations": [
    {
      "to": [
        {
          "email": "john@example.com"
        }
      ],
      "subject": "Hello, World!"
    }
  ],
  "from": {
    "email": "from_address@example.com"
  },
  "content": [
    {
      "type": "text/plain",
      "value": "Hello, World!"
    }
  ]
  "attachements": [
    {
      "filename": "helloworld.csv",
      "content": "The Base64 encoded content of the attachment."
    }
  ]
}
```

Before we translate this request into Clojure, we need to be able to convert our csv string into a base64 encoded string. We can do this with `java.util.Base64`.

```clojure
(defn encode-string-to-base64 [string]
  (.encodeToString
   (java.util.Base64/getEncoder)
   (.getBytes string)))
```

Finally, we write a function to make the request to the SendGrid API.

```clojure
(defn send-email-with-csv [to-email csv-string]
  (http/post
   "https://api.sendgrid.com/v3/mail/send"
   {:headers      {:authorization
                   (str "Bearer "
                        (System/getenv "SENGRID_API_KEY"))}
    :content-type :json
    :form-params
    {:personalizations [{:to      [{:email to-email}]
                         :subject "Hello, World!"}]
     :from             {:email "from_address@exampl.com"}
     :content          [{:type  "text/plain"
                         :value "Hello, World!"}]
     :attachments
     [{:filename "helloworld.csv"
       :content  (encode-string-to-base64 csv-string)}]}}))
```

That's all there is to it. Here's all the code.

```clojure
(ns clj-sendgrid-example.core
  (:require [clj-http.client :as http]
            [clojure.string :as str]))

(def data
  [{:name "Bob" :age 27 :favourite-food "bagels"}
   {:name "Sarah" :age 23 :favourite-food "apples"}
   {:name "John" :age 41 :favourite-food "pasta"}])

(defn escape-csv-value [value]
  (str "\"" value "\""))

(defn row->csv-row [row]
  (->> (map escape-csv-value row)
       (str/join ",")))

(defn ms->csv-string [ms]
  (let [columns (keys (first ms))
        headers (map name columns)
        rows    (map #(map % columns) ms)]
    (->> (into [headers] rows)
         (map row->csv-row)
         (str/join "\n"))))

(defn encode-string-to-base64 [string]
  (.encodeToString
   (java.util.Base64/getEncoder)
   (.getBytes string)))

(defn send-email-with-csv [to-email csv-string]
  (http/post
   "https://api.sendgrid.com/v3/mail/send"
   {:headers      {:authorization
                   (str "Bearer "
                        (System/getenv "SENGRID_API_KEY"))}
    :content-type :json
    :form-params
    {:personalizations [{:to      [{:email to-email}]
                         :subject "Hello, World!"}]
     :from             {:email "from_address@exampl.com"}
     :content          [{:type  "text/plain"
                         :value "Hello, World!"}]
     :attachments
     [{:filename "helloworld.csv"
       :content  (encode-string-to-base64 csv-string)}]}}))

(comment
  (->> data
       ms->csv-string
       (send-email-with-csv "john@example.com")))
```

The full example project can be found [here](https://github.com/andersmurphy/clj-cookbook/tree/master/sending-email/sendgrid-example).
