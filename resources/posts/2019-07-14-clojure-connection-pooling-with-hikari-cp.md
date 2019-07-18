Title: Clojure: connection pooling with hikari-cp

Connection pooling is a technique for improving app performance. A pool (cache) of reusable connections is maintained meaning when users connect to the database they can reuse an existing connection. When the user finishes using the connection it is placed back in the pool for other users to use. This reduces the overhead of connecting to the database by decreasing network traffic, limiting the cost of creating new connections, and reducing the load on the garbage collector. Effectively improving the
responsiveness of your app for any task that requires connecting to the database.

This guide will use [hikari-cp](https://github.com/tomekw/hikari-cp) a Clojure wrapper around [HikariCP](https://github.com/brettwooldridge/HikariCP) a java database connection pooling library. It assumes you are using [leiningen](https://leiningen.org) as your build/dependency management tool and have a [postgresql](https://www.postgresql.org) database set up and running. It also won't cover using environment variables to store your database url out of source control (which is highly recommended for security).

### Initial set up

Start postgres.

```bash
pg_ctl -D pg start
```

Create a database called `databasename`.

```bash
createdb databasename
```

### Dependencies

Add `org.clojure/java.jdbc`, `org.postgresql/postgresql` and `hikari-cp` to your dependencies.

```clojure
(defproject hikari-cp-example "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/java.jdbc "0.7.7"]
                 [org.postgresql/postgresql "42.2.3"]
                 [hikari-cp "2.7.1"]])
```

### Parsing a database URL

Create a helper function for passing urls.

```clojure
(ns hikari-cp-example.core
  (:require [clojure.java.jdbc :as sql]
            [hikari-cp.core :as cp]
            [clojure.string :as str]))

(defn db-info-from-url [db-url]
  (let [db-uri              (java.net.URI. db-url)
        [username password] (str/split (or (.getUserInfo db-uri) ":") #":")]
    {:username      (or username (System/getProperty "user.name"))
     :password      (or password "")
     :port-number   (.getPort db-uri)
     :database-name (str/replace-first (.getPath db-uri) "/" "")
     :server-name   (.getHost db-uri)}))

```

This function parses urls that are of the form `postgresql://username:password@localhost:5432/databasename"`. It also handles defaults if certain parameters are missing.

### Setting up the connection pool

Define the connection.

```clojure
(def datasource-options
  (merge (db-info-from-url "postgresql://localhost:5432/databasename")
         {:auto-commit        true
          :read-only          false
          :adapter            "postgresql"
          :connection-timeout 30000
          :validation-timeout 5000
          :idle-timeout       600000
          :max-lifetime       1800000
          :minimum-idle       10
          :maximum-pool-size  20
          :pool-name          "db-pool"
          :register-mbeans    false}))
```

Define a connection creating function using `defonce` to ensure it is only create once and `delay` to make it lazy.

```clojure
(defonce datasource
  (delay (cp/make-datasource datasource-options)))
```

Create the connection.

```clojure
(def database-connection {:datasource @datasource})
```

You should see some logs from hikari start to flood the repl.

```clojure
...
16:28:58.495 [nRepl-session-7849a8f5-c0c2-4a6e-99f7-af9c375c9653] DEBUG com.zaxxer.hikari.HikariConfig - schema..........................none
16:28:58.495 [nRepl-session-7849a8f5-c0c2-4a6e-99f7-af9c375c9653] DEBUG com.zaxxer.hikari.HikariConfig - threadFactory...................internal
16:28:58.495 [nRepl-session-7849a8f5-c0c2-4a6e-99f7-af9c375c9653] DEBUG com.zaxxer.hikari.HikariConfig - transactionIsolation............default
16:28:58.495 [nRepl-session-7849a8f5-c0c2-4a6e-99f7-af9c375c9653] DEBUG com.zaxxer.hikari.HikariConfig - username........................"anders"
16:28:58.495 [nRepl-session-7849a8f5-c0c2-4a6e-99f7-af9c375c9653] DEBUG com.zaxxer.hikari.HikariConfig - validationTimeout...............5000
16:28:58.497 [nRepl-session-7849a8f5-c0c2-4a6e-99f7-af9c375c9653] INFO com.zaxxer.hikari.HikariDataSource - db-pool - Starting...
...
```

### Stop the logs flooding the repl

The hikari logs can be quite noisy. Logback can be used to filter out the DEBUG and INFO level messages.

Add logback as a dependency.

```clojure
(defproject hikari-cp-example "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/java.jdbc "0.7.7"]
                 [org.postgresql/postgresql "42.2.3"]
                 [hikari-cp "2.7.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]])
```

Create a `logback.xml` in your project `resources` folder (lein will add this to the class path).

```xml
<configuration debug="false">
 <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %-10contextName %logger{36} - %msg%n</pattern>
    </encoder>
 </appender>
 <logger name="com.zaxxer.hikari">
     <level value="error"/>
 </logger>
  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
  </root>
</configuration>
```

Now only ERROR level hikari messages will be logged.

This concludes this guide to setting up connection pooling with hikari-cp. The full example project can be found [here](https://github.com/andersmurphy/clj-cookbook/tree/master/connection-pooling/hikari-cp-example).
