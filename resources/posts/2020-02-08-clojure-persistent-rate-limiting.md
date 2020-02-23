Title: Clojure: persistent rate limiting

Some business needs require you to limit the number of times you do something. An example of this would be sending a daily email to users. You could achieve this by making sure you run the function only once per day. However, if that function were to crash part way through how would you know which users had already been sent their daily email and which hadn't? Resolving this without sending some users multiple emails could be a large time sink. A more robust solution would be to make the email sending function idempotent; meaning the effects of the function are applied only once per user per day and any additional applications do nothing. This article will explore one approach to solving this problem in Clojure.

As this is a solution that relies on persistence, we first need to set up a database:

```Clojure
(ns do-once.core
  (:require [next.jdbc :as jdbc]))

(def db {:dbtype "postgresql" :dbname "databasename"})
(def ds (jdbc/get-datasource db))
```

We need a function that queries the database to check if a task has already been done:

```Clojure
(defn done? [uuid name]
  (jdbc/execute-one! ds ["
select * from do_once where uuid = ? and name = ?"
                         uuid name]))
```

We need a function that records a task that has been done:

```Clojure
(defn do! [uuid name]
  (jdbc/execute! ds ["
insert into do_once (uuid, name) values (? , ?) on conflict (uuid, name) do nothing"
                     uuid name]))
```

We need a table for recording our tasks:

```Clojure
(jdbc/execute! ds ["
create table do_once (
  pid serial primary key,
  uuid text not null,
  name text not null)"])

  (jdbc/execute! ds ["
create unique index do_once_unique ON do_once(uuid, name)"])
```

We need a macro that records the task:

```Clojure
(defmacro do-once! [uuid name & body]
  `(when-not (done? ~uuid ~name)
     (do! ~uuid ~name)
     ~@body))
```

We can now send an email to Nora:

```Clojure
(do-once! "Nora" "email-2020-02-08"
            (println "email sent")
            (prn (+ 1 2 3 4)))

=> email sent

10
```

If we try to send Nora a second email that day, it doesn't get sent:

```Clojure
(do-once! "Nora" "email-2020-02-08"
            (println "email sent")
            (prn (+ 1 2 3 4)))

=> nil
```

We can use a macro with named parameters to make things more explicit:

```Clojure
(defmacro do-once-2! [& {:keys [uuid name action]}]
  `(when-not (done? ~uuid ~name)
     (do! ~uuid ~name)
     ~action))

(do-once-2! :uuid "Nora"
            :name "email-2020-02-09"
            :action (do (println "email sent")
                        (prn (+ 1 2 3 4))))

=> email sent

10
```

Or just a plain old function that takes data:

```Clojure
(defn do-once-3! [{:keys [uuid name action]}]
  (when-not (done? uuid name)
    (do! uuid name)
    (action)))

(do-once-3! {:uuid   "Nora"
             :name   "email-2020-02-10"
             :action (fn []
                        (println "email sent")
                        (prn (+ 1 2 3 4)))})

=> email sent

10
```

That covers this approach to persistent rate limiting in Clojure. The full example project can be found [here](https://github.com/andersmurphy/clj-cookbook/tree/master/rate-limiting/do-once).
