Title: Clojure: clj-kondo datalog linting

[clj-kondo](https://github.com/clj-kondo/clj-kondo) has had really nice [datalog syntax checking](https://github.com/clj-kondo/clj-kondo/releases/tag/v2019.11.07) for a while. However, on some projects you might have wrapped the underlying datalog  query function `q` or be using a datalog implementation clj-kondo doesn't know about. Thankfully, it's trivial to add linting support to any implementation of the query function `q` as long as it conforms to the Datalog dialect of [Datomic](https://docs.datomic.com/pro/query/query.htmlu).

## Example of a wrapped q implementation

```clojure
(ns example.db
  (:require [datalevin.core :as d]))
  
(defn q [query & inputs]
  (apply d/q query inputs))
```

## Update linter configuration

At the root of your project you should have a `config.edn` file located in the `.clj-kondo` directory.

```
├── .clj-kondo
│   └── config.edn
```

Add `:lint-as {example.db/q datascript.core/q}` to the `config.edn` file.

```clojure
{:lint-as {example.db/q datascript.core/q}}
```

This will tell clj-kondo to lint our `q` function the same way it would lint `datascript.core/q`.

That's it. You should now have nice lint messages for datalog queries.



