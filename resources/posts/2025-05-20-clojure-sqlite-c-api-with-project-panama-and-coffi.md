title: Clojure: SQLite C API with project Panama and Coffi

In this post I'll go over using [coffi](https://github.com/IGJoshua/coffi) to build a Clojure SQLite wrapper.

## Build SQLite

First we need to compile SQLite for our machine see [this post for details](https://andersmurphy.com/2023/07/09/sqlite-building-from-source-on-macos.html). 

## Load the SQLite Library

We can load a Library with ffi/load-library.

```clojure
(ffi/load-library "resources/sqlite3.so")
```

## Open a connection

To open a connection we need to implement `sqlite3-open-v2`. The SQLite documentation is really good and they have the C function type signatures so this is mostly a question of reading the docs and translating.

```clojure
(defcfn sqlite3-open-v2
  "sqlite3_open_v2" [::mem/c-string ::mem/pointer ::mem/int
                     ::mem/c-string] ::mem/int
  sqlite3-open-native
  [filename flags]
  (with-open [arena (mem/confined-arena)]
    (let [pdb    (mem/alloc-instance ::mem/pointer arena)
          result (sqlite3-open-native filename pdb flags nil)]
      (if (sqlite-ok? result)
        (mem/deserialize-from pdb ::mem/pointer)
        (throw (ex-info "Failed to open sqlite3 database"
                 {:filename filename}))))))
```

A few things going on here, the `(with-open [arena (mem/confined-arena)]` pattern is quite common when working with an out variables. An out variable tends to be a pointer you pass in and will get set to a value you want out. In this case the db object.

## Close a connection

We can define `close` as follows.

```clojure
(defcfn sqlite3-close
  sqlite3_close
  [::mem/pointer] ::mem/int)
```

## Prepare a statement

Now the big reason I went down this rabbit hole is I wanted to be able to cache prepared statements. Enter `prepare-v2`.

```clojure
(defcfn sqlite3-prepare-v2
  "sqlite3_prepare_v2"
  [::mem/pointer ::mem/c-string ::mem/int
   ::mem/pointer ::mem/pointer] ::mem/int
  sqlite3-prepare-native
  [pdb sql]
  (with-open [arena (mem/confined-arena)]
    (let [ppStmt (mem/alloc-instance ::mem/pointer arena)
          code   (sqlite3-prepare-native pdb sql -1 ppStmt
                   nil)]
      (if (sqlite-ok? code)
        (mem/deserialize-from ppStmt ::mem/pointer)
        (throw (ex-info "Failed to create preparde statement"
                 {:stmt sql}))))))
```

Similar to the `open` function we get an out pointer that we deserialize into Clojure land so we can pass it around to other function.

## Reset a statement and clear bindings

When you reuse prepared statements in SQLite you need to reset and clear any bindings from it so that it can be re-used. 

```
(defcfn sqlite3-reset
  sqlite3_reset
  [::mem/pointer] ::mem/int)
  
(defcfn sqlite3-clear-bindings
  sqlite3_clear_bindings
  [::mem/pointer] ::mem/int)
```

## Bind values

Prepared statements are no use without bindings. Thankfully, SQLite has some very basic types, so we are only going to implement: `bind-int`, `bind-float` and `bind-text`.

```clojure
(defcfn sqlite3-bind-int
  sqlite3_bind_int
  [::mem/pointer ::mem/int ::mem/int] ::mem/int)
  
(defcfn sqlite3-bind-double
  sqlite3_bind_double
  [::mem/pointer ::mem/int ::mem/double] ::mem/int)
```

`bind-text` is a little more challenging as we need to define a constants defining special destructor behaviour.

```clojure
(def sqlite-transient (mem/as-segment -1))

(defcfn sqlite3-bind-text
  "sqlite3_bind_text"
  [::mem/pointer ::mem/int ::mem/c-string ::mem/int
   [::ffi/fn [::mem/pointer] ::mem/void]] ::mem/int
  sqlite3-bind-text-native
  [pdb idx text]
  (let [text (str text)]
    (sqlite3-bind-text-native pdb idx text
      (count (String/.getBytes text "UTF-8"))
      sqlite-transient)))
```

Finally we need to iterate through our params to bind them based on their type.

```clojure
(defn type->sqlite3-bind [param]
  (cond
    (integer? param) sqlite3-bind-int
    (double? param)  sqlite3-bind-double
    :else            sqlite3-bind-text))
    
(defn bind-params [stmt params]
  (doall
    (map-indexed
      (fn [i param]
        ;; starts at 1
        ((type->sqlite3-bind param) stmt (inc i) param)) params)))
```

## Cache prepared statements

We use `clojure.core.cache` for caching our statements. Each connection will have it's own cache.

```clojure
(defn prepare-cached [{:keys [pdb stmt-cache]} [sql & params]]
  (let [stmt (cache/lookup-or-miss stmt-cache sql
               (fn [sql] (sqlite3-prepare-v2 pdb sql)))]
    (bind-params stmt params)
    stmt))
``` 

## Our internal query function

To run a SQL query we need to do the following:

1. Create the prepared statement object using sqlite3_prepare_v2().
2. Bind values to parameters using the sqlite3_bind_*() interfaces.
3. Run the SQL by calling sqlite3_step() one or more times.
4. Reset the prepared statement using sqlite3_reset() then go back 
    to step   2. Do this zero or more times.
5. Destroy the object using sqlite3_finalize().

To keep things simple, we will return all values at TEXT and we won't finalise the statements as they are cached and will be taken care of by the JVM on shutdown.

```clojure
(defcfn sqlite3-column-count
  sqlite3_column_count
  [::mem/pointer] ::mem/int)

(defcfn sqlite3-column-text
  sqlite3_column_text
  [::mem/pointer ::mem/int] ::mem/c-string)

(defn- q* [stmt & [row-builder]]
  (let [cols (range (sqlite3-column-count stmt))
        rs   (loop [rows (transient [])]
               (let [step (sqlite3-step stmt)]
                 (cond
                   (= step 100)
                   (when row-builder
                     (recur (->> (mapv #(sqlite3-column-text stmt %)
                                   cols)
                              row-builder
                              (conj! rows))))

                   (= step 101) (persistent! rows)
                   :else        :error)))]
    (sqlite3-reset stmt)
    (sqlite3-clear-bindings stmt)
    rs))
```

## Configuring a connection

For web servers the SQLite defaults are not great, so for each connection we want to set a few pragma to tune SQLite for concurrent users.

```clojure
(defn new-conn! [db-name read-only]
  (let [flags           (if read-only
                          ;; SQLITE_OPEN_READONLY
                          0x00000001
                          ;; SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE
                          (bit-or 0x00000002 0x00000004))
        *pdb            (sqlite3-open-v2 db-name flags)
        statement-cache (cache/fifo-cache-factory {} :threshold 512)
        conn            {:pdb        *pdb
                         :stmt-cache statement-cache}]
    (q* (sqlite3-prepare-v2 *pdb
          (str
            "pragma cache_size = 15625;"
            "pragma page_size = 4096;"
            "pragma journal_mode = WAL;"
            "pragma synchronous = NORMAL;"
            "pragma temp_store = MEMORY;"
            "pragma foreign_keys = false;")))
    conn))
```

## Initialising a db and pool

Next we need a function to initialise our db connection pool. In practice I tend to have a read only pool with as many connections as cores, and a single writer pool with one connection.

```clojure
(defn init-db!
  [db-name & [{:keys [pool-size read-only]
               :or   {pool-size 4}}]]
  (let [conns (repeatedly pool-size
                (fn [] (new-conn! db-name read-only)))
        pool  (LinkedBlockingQueue/new ^int pool-size)]
    (run! #(LinkedBlockingQueue/.add pool %) conns)
    {:conn-pool pool
     :close
     (fn [] (run! (fn [conn] (sqlite3-close (:pdb conn))) conns))}))
```

We use a `LinkedBlockingQueue` as it fits well for managing resources like connections. Note: connection pools like HikariCP use a custom concurrent data [structure called a concurrentBag](https://github.com/brettwooldridge/HikariCP/wiki/Down-the-Rabbit-Hole#concurrentbag) for extra performance. But, were going to keep things simple.

## User facing query function

We wrap our internal query function in one that handles the pool for us.

```
(defn q [{:keys [conn-pool]} query & [row-builder]]
  (let [conn   (LinkedBlockingQueue/.take conn-pool)
        stmt   (prepare-cached conn query)]
    (try
      (q* stmt row-builder)
      (finally
        (LinkedBlockingQueue/.offer conn-pool conn)))))
```

## Some naive benchmarks

Keep in mind this benchmark is very naive and narrow and is not indicative of the overall performance.

```clojure
(defonce db
  (d/init-db! "database.db"
    {:read-only true
     :pool-size 4}))
```

We're running this against my one billion checkboxes demo's db. 

```clojure
(user/bench
  (d/q db
    ["SELECT chunk_id, state FROM cell WHERE chunk_id IN (?, ?, ?, ?, ?, ?, ?, ?, ?)"
     1978 3955 5932 1979 3956 5933 1980 3957 5934]
    (fn [[chunk-id state]] {:chunk-id chunk-id :state state})))
;; Execution time mean : 545.401696 µs
```

This is 3x faster than the original version using JDBC and HikariCP. Of course there's a long way to go to make this feature complete. But, it's promising non the less.

## Conclusion

Hopefully, this whistle stop tour of using SQLite C API has been useful.

You can find the source code for this blog post in the nascent [sqlite4clj library here](https://github.com/andersmurphy/sqlite4clj). Though the library is far from feature complete it is currently running in production in my [One Billion Checkboxes Demo](https://checkboxes.andersmurphy.com).

A huge thanks to [Joshua Suskalo](https://github.com/sponsors/IGJoshua) for [coffi](https://github.com/IGJoshua/coffi) and being super helpful on clojure slack. If you are curious about project Panama and the new java FFI interface I'd highly recommend taking coffi for a spin.
