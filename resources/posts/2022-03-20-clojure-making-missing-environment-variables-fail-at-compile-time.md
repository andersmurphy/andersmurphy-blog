Title: Clojure: making missing environment variables fail at compile time

Have you ever deployed something to production only to have it break because of a missing environment variable? You can eliminate this risk with a simple macro.

First we write a regular function that throws an exception if `System/getenv` can't find an environment variable. This gives us a runtime check.

```clojure
(defn env-dynamic
  [k]
  (if-let [v (-> k name System/getenv)]
    v
    (throw (ex-info (str "Missing env variable: " k)
                    {:missing-env-variable k}))))
```

We then wrap it in a macro.

```clojure
(defmacro env
  [k]
  (env-dynamic k))
```

This turns it into a compile time check.

```clojure
(env :DATABASE_URL)

=>
Missing env variable: :DATABASE_URL
```

For this to work you will need to have access to environment variables at compile time. In practice this require your build pipeline to have access to the same environment variables as production.

The `env-dynamic` function is useful for environment variables that differ between processes for example `PORT` environment variable on a heroku dyno.

This macro guarantees your build will fail at compile time so production won't break because of a missing environment variable.
