title: 100000 TPS over a billion rows: the unreasonable effectiveness of SQLite

SQLite doesn't have MVCC! It only has a single writer! SQLite is for phones and mobile apps (and the occasional airliner)! For web servers use a proper database like Postgres! In this article I'll go over why  being embedded and a single writer are not deficiencies but actually allow SQLite to scale so unreasonably well.

## Prelude

For the code examples I will be using Clojure. But, what they cover should be applicable to most programming language.

The machine these benchmarks run on has the following specs:

- MacBook Pro (2021)
- Chip: Apple M1 Pro
- Memory: 16 GB

These benchmarks are not meant to be perfect or even optimal. They are merely to illustrate that it's relatively easy to achieve decent write throughput with SQLite. Usual benchmark disclaimers apply. 

##  Defining TPS

When I say TPS I don't mean writes/updates per second. I'm talking about transactions per second, specifically interactive transactions that are common when building web applications. By interactive transactions I mean transactions where you execute some queries, run some application code and then execute more queries. For example:

```SQL
BEGIN;
UPDATE accounts SET balance = balance - 100.00
    WHERE name = 'Alice';
-- some application code runs
UPDATE accounts SET balance = balance + 100.00
    WHERE name = 'Bob';
COMMIT;
```

Transactions are useful because they let you rollback the state of your changes if your application encounters a problem.

## The benchmark harness 

To simulate requests we spin up `n` virtual threads (green threads) that each execute a function `f` this is analogous to handlers on a web server and will give us similar contention. Worth noting that this is high burst. I.e we will reach `n` level concurrent requests as fast as the system can spin up the virtual threads.

```clojure
(defmacro tx-per-second [n & body]
  `(let [ids#   (range 0 ~n)
         start# (. System (nanoTime))]
     (->> ids#
       ;; Futures are using virtual threads so blocking is not slow
       (mapv (fn [_#] (future ~@body)))
       (run! deref))
     (int (/ ~n (/ (double (- (. System (nanoTime)) start#)) 1000000000.0)))))
```

For the Clojure programmers among you `future` has been altered to use virtual threads. So, we can spin up millions if we need to.

```clojure
;; Make futures use virtual threads
(set-agent-send-executor!
  (Executors/newVirtualThreadPerTaskExecutor))
(set-agent-send-off-executor!
  (Executors/newVirtualThreadPerTaskExecutor))
```

We'll be using Postgres  as our network database (I'm using Postgres, but the same applies to MySQL etc) with a high performance connection pool optimised for our number of cores. 

```clojure
(defonce pg-db
  (jdbc/with-options
    (connection/->pool
      HikariDataSource
      {:dbtype          "postgres"
       :dbname          "thedb"
       :username        (System/getProperty "user.name")
       :password        ""
       :minimumIdle     8
       :maximumPoolSize 8})
    {}))
```

We'll be using SQLite with a single writer connection and a number of reader connections equal to our number of cores.

```clojure 
(defonce lite-db
  (d/init-db! "database.db"
    {:pool-size 8
     :pragma {:cache_size         15625
              :page_size          4096
              :journal_mode       "WAL"
              :synchronous        "NORMAL"
              :temp_store         "MEMORY"
              :busy_timeout       5000}}))
```

Our databases will have a simple schema:

```clojure
(jdbc/execute! pg-db
  ["CREATE TABLE IF NOT EXISTS account(id INT PRIMARY KEY, balance INT)"])
(d/q (lite-db :writer)
  ["CREATE TABLE IF NOT EXISTS account(id PRIMARY KEY, balance INT)"])
```

And each contain a billion rows:

```clojure
(->> (range 0 (* 1000 1000 1000))
  (partition-all 32000)
  (run!
    (fn [batch]
      (jdbc-sql/insert-multi! pg-db :account
        (mapv (fn [id] {:id id :balance 1000000000}) batch)))))
        
(->> (range 0 (* 1000 1000 1000))
  (partition-all 100000)
  (run!
    (fn [batch]
      (d/with-write-tx [tx (lite-db :writer)]
        (run!
          (fn [id]
            (d/q tx
              ["INSERT INTO account(id, balance) VALUES (?,?)" id 1000000000]))
          batch)))))
```

Our user distribution will follow a [power law](https://en.wikipedia.org/wiki/Power_law). I.e the top X percent will be involved in most of the transactions. We have a billion users, so in practice most of those won't be active, or be active rarely. `0.9995` means 99.95% of transactions will be done by 0.05% of users. This still means around 100000 unique active users at any given time. 

The reason we are using a power law, is that's a very common distribution for a lot of real products. If you think about a credit card payment system, in the context of retail, the largest number of transactions are most likely with a few large retailers (Amazon, Walmart etc).

```clojure
(defn pareto-user []
  (rand-pareto (* 1000 1000 1000) 0.9995))
```

`rand-pareto` turns a random distribution into a power law distribution.

```clojure
(defn rand-pareto [r p]
  (let [a (/ (Math/log (- 1.0 p)) (Math/log p))
        x (rand)
        y (/ (- (+ (Math/pow x a) 1.0)
               (Math/pow (- 1.0 x) (/ 1.0 a)))
            2.0)]
    (long (* r y))))
```

## Network database

Let's start with a network database.

```clojure
(tx-per-second 100000
  (jdbc/with-transaction [tx pg-db]
    (jdbc/execute! tx (credit-random-account))
    (jdbc/execute! tx (debit-random-account))))
    
;; => 13756 TPS
```

A respectable 13756 TPS.

However, normally a network database will not be on the same server as our application. So let's simulate some network latency. Let's say you have 5ms latency between your app server and your database.

```clojure
(tx-per-second 10000
  (jdbc/with-transaction [tx pg-db]
    (jdbc/execute! tx (credit-random-account))
    (Thread/sleep 5)
    (jdbc/execute! tx (debit-random-account))))
    
;; => 1214 TPS
```

*Note: virtual threads do not sleep a real thread. They instead park allowing the underlying carrier thread to resume another virtual thread.*

What if we increase that latency to 10ms?

```clojure
(tx-per-second 10000
  (jdbc/with-transaction [tx pg-db]
    (jdbc/execute! tx (credit-random-account))
    (Thread/sleep 10)
    (jdbc/execute! tx (debit-random-account))))
    
;; => 702 TPS
```

But, wait our transactions are not serialisable, which they need to be if we want consistent transaction processing (SQLite is isolation serialisable by design). We better fix that and handle retries.

```clojure
(tx-per-second 10000
  (loop []
    (let [result
          (try
            (jdbc/with-transaction [tx pg-db {:isolation :serializable}]
              (jdbc/execute! tx (credit-random-account))
              (Thread/sleep 10)
              (jdbc/execute! tx  (debit-random-account)))
            (catch Exception _ nil))]
      (when-not result (recur)))))

;; => 660 TPS
```

What if the interactive transaction has an extra query (an extra network hop)?

```clojure
(tx-per-second 10000
  (loop []
    (let [result
          (try
            (jdbc/with-transaction [tx pg-db {:isolation :serializable}]
              (jdbc/execute! tx (credit-random-account))
              (Thread/sleep 10)
              (jdbc/execute! tx  (debit-random-account))
              (Thread/sleep 10)
              (jdbc/execute! tx  (debit-random-account)))
            (catch Exception _ nil))]
      (when-not result (recur)))))

;; => 348 TPS
```

348 TPS! What's going on here? [Amdahl's Law](https://en.wikipedia.org/wiki/Amdahl%27s_law) strikes!

>the overall performance improvement gained by optimizing a single part of a system is limited by the fraction of time that the improved part is actually used.

We're holding transactions with row locks across a network with high contention because of the power law. What's terrifying about this is no amount of additional (cpu/servers/memory) is going to save us. This is a hard limit caused by the network. What's worse, in any unexpected increase in latency will exacerbate the problem. Which also means you can't have application servers in different data centres than your database (because of the increased latency). 

I learnt this the hard way building an emoji based tipping bot for discord. At the time I didn't understand why we were hitting this hard limit in TPS. We ended up sacrificing the convenience of interactive transactions and moving everything into stored procedures (meaning no locks across the network). However, in a lot of domains this isn't possible.

## Embedded means no network

Let's see how SQLite fares.

```clojure
(tx-per-second 1000000
  (d/with-write-tx [tx (lite-db :writer)]
    (d/q tx (credit-random-account))
    (d/q tx (debit-random-account))))

;; => 44096 TPS
```

44096 TPS! By eliminating the network SQLite massively reduces the impact of Amdahl's law.

## Single writer lets you batch

We don't need to stop there though. Because, SQLite is a single writer we can batch. [sqlite4clj](https://github.com/andersmurphy/sqlite4clj) provides a convenient dynamic batching function. Batch size grows dynamically with the workload and producers don't have to block when the consumer is busy. Effectively it self optimises for latency and throughput.

```clojure
(defn batch-fn [db batch]
  @(on-pool! lite-write-pool
     (d/with-write-tx [tx db]
       (run! (fn [thunk] (thunk tx)) batch))))
       
(defonce tx!
  (b/async-batcher-init! lite-db
    {:batch-fn #'batch-fn}))
```

*Note: to Clojure/Java programmers we're using a thread pool as SQLite should be treated as CPU not IO, so we don't want it starving our virtual threads (io green threads).*

```clojure
(tx-per-second 1000000
  @(tx!
     (fn [tx]
       (d/q tx (credit-random-account))
       (d/q tx (debit-random-account)))))
       
;; => 186157 TPS
```

But, wait I hear you cry! That's cheating we now don't have isolated transaction failure. Batching is sacrificing fine grained transaction. You're right! Let's fix that.

```clojure
(tx-per-second 1000000
  @(tx!
     (fn  [tx]
       (d/q tx ["SAVEPOINT inner_tx"])
       (try
         (d/q tx (credit-random-account))
         (d/q tx (debit-random-account))
         (catch Throwable _
           (d/q tx ["ROLLBACK inner_tx"])))
       (d/q tx ["RELEASE inner_tx"]))))
       
;; => 121922 TPS
```

SQLite supports nested transactions with `SAVEPOINT` this lets us have fine-grained transaction rollback whilst still batching our writes. If a transaction fails it won't cause the batch to fail. The only case where the whole batch will fail is in the case of power loss/or a hard crash.

## What about concurrent reads?

Generally systems have a mix of reads and writes, somewhere in the region of 75% reads to 25% writes. So let's add some writes.

```clojure
(tx-per-second 1000000
  (on-pool! lite-read-pool
    (d/q (lite-db :reader)
      ["select * from account where id = ? limit 1" (pareto-user)]))
  (on-pool! lite-read-pool
    (d/q (lite-db :reader)
      ["select * from account where id = ? limit 1" (pareto-user)]))
  (on-pool! lite-read-pool
    (d/q (lite-db :reader)
      ["select * from account where id = ? limit 1" (pareto-user)]))
  @(tx!
     (fn  [tx]
       (d/q tx ["SAVEPOINT inner_tx"])
       (try
         (d/q tx (credit-random-account))
         (d/q tx (debit-random-account))
         (catch Throwable _
           (d/q tx ["ROLLBACK inner_tx"])))
       (d/q tx ["RELEASE inner_tx"]))))
       
;; => 102545 TPS
```

102545 TPS!

*Note: to Clojure/Java programmers we're using a separate read thread pool so that reads don't starve writes.*

## TPS Report

|                         | Postgres | SQLite   |
| ----------------------- | -------- | -------- |
| no network              | 13756    | 44096    |
| 5ms                     | 1214     | n/a      |
| 10ms                    | 702      | n/a      |
| 10ms serializable       | 660      | n/a      |
| batch                   | n/a      | 186157   |
| batch savepoint         | n/a      | 121922   |
| batch savepoint + reads | n/a      | 102545   |

## Conclusion

Hopefully, this post helps illustrate the unreasonable effectiveness of SQLite as well as the challenges you can run in with Amdahl's law and network databases like postgres.

The full benchmark code [can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/sqlite-vs-postgres).

**Further Reading:**

If you want to learn more about Amdahl's law, power laws and how they interact with network databases I highly recommend listening to [this interview with Joran Greef](https://www.youtube.com/watch?v=9oyhNDv882U) and watching his talk
[1000x: The Power of an Interface for Performance by Joran Dirk Greef](https://www.youtube.com/watch?v=yKgfk8lTQuE).
  
If you want to read about how much further you can scale SQLite checkout [Scaling SQLite to 4M QPS on a single server (EC2 vs Bare Metal)](https://use.expensify.com/blog/scaling-sqlite-to-4m-qps-on-a-single-server).

If you're thinking of running SQLite in production and wondering how to create streaming replicas, backups and projections checkout [litestream](https://litestream.io).

If you still don't think a single machine can handle your workload it's worth reading [Scalability! But at what COST?](https://www.usenix.org/system/files/conference/hotos15/hotos15-paper-mcsherry.pdf).

**Thanks to** Everyone on the [Datastar discord](https://discord.gg/bnRNgZjgPh) who read drafts of this and gave me feedback.

**Discussion**

- [hackernews](https://news.ycombinator.com/item?id=46124205)
- [reddit](https://www.reddit.com/r/Clojure/comments/1pchdr3/sqlite4clj_100k_tps_over_a_billion_rows_the/)
