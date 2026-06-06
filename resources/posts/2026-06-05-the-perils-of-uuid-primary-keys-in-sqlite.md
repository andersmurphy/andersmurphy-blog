title: The perils of UUID primary keys in SQLite

It's common to use random UUIDs as a primary key in databases. One of the known downsides of random UUIDs is that their unordered nature (UUID4) can cause a lot of extra paging for the clustered index because you are inserting rows randomly into the Btree and having to re-balance it. This post tries to help us develop a more visceral understanding of the performance cost of all that extra paging.

While this post is about SQLite specifically, the problem of random UUIDs also extends to other databases that use clustered indexes.

## What is a clustered index?

A clustered index determines the physical storage order of the rows in a table. Because of this:

- There can be only one clustered index per table (rows can only be physically sorted one way).
- A clustered index is the table.
- A non-clustered index, by contrast, stores only the indexed columns plus a pointer to the actual row data, which lives elsewhere.

## Rowid

Every ordinary SQLite table has an implicit 64-bit integer primary key called rowid. The table's data is stored in a B-tree structure containing one entry for each table row, using the rowid value as the key. This is effectively SQLite's clustered index. The physical storage order of rows follows rowid sequence.

## Without rowid 

SQLite also supports WITHOUT ROWID tables. These tables have no implicit rowid. Instead, the primary key you declare becomes the clustered index.

*Note: In SQLite rowid tables are implemented as B*-Trees where all content is stored in the leaves of the tree, whereas WITHOUT ROWID tables are implemented using ordinary B-Trees with content stored on both leaves and intermediate nodes.*

## Baseline

Let's establish a performance baseline with regular rowid int primary key. We'll insert 10 million rows in batches of 1 million. 

```clojure
(d/q writer
  ["CREATE TABLE IF NOT EXISTS event(id INT PRIMARY KEY, data BLOB)"])
      
(dotimes [_ 100]
  (time
    (d/with-write-tx [db writer]
      (dotimes [_ 1000000]
        (d/q db ["INSERT INTO event (data) values (?)" data])))))
```

Results:

| total rows | time in ms |
| ---------- | ---------- |
|  10000000  | 1208       |
|  20000000  | 1102       |
|  30000000  | 1177       |
|  40000000  | 1138       |
|  50000000  | 1086       |
|  60000000  | 1101       |
|  70000000  | 1070       |
|  80000000  | 1069       |
|  90000000  | 1079       |
| 100000000  | 1081       |

Roughly a million inserts per second.

## UUID4

Now lets try UUID4.

```clojure
(d/q writer
  ["CREATE TABLE IF NOT EXISTS event(id BLOB PRIMARY KEY, data BLOB) WITHOUT ROWID"])

(dotimes [_ 10]
  (time
    (d/with-write-tx [db writer]
      (dotimes [_ 1000000]
        (d/q db ["INSERT INTO event (id, data) values (?, ?)"
                 (random-uuid4-bytes) data])))))
```

Results:

| total rows | time in ms |
| ---------- | ---------- |
| 10000000   | 2649       |
| 20000000   | 5644       |
| 30000000   | 7137       |
| 40000000   | 8352       |
| 50000000   | 9359       |
| 60000000   | 9817       |
| 70000000   | 10490      |
| 80000000   | 11130      |
| 90000000   | 11668      |
| 100000000  | 12586      |

Oh no! What's happened here the inserts are 10-12x slower?!

## Profile

That's a big difference. But, lets not guess when we can profile.

Below is a normalised diffgraph. A diffgraph compares two profiling snapshots (in this case INT vs UUID4) and displays the differences in a flamegraph structure. Unlike a regular diffgraph that shows absolute changes, a normalised view adjusts the total number of samples between the two compared profiles to be the same. This means we can see the relative differences as a percentage. This matters because our profiles will run for different amounts of time.

<img src="/assets/diffgraph_perils.png" alt="diffgraph" style="width: 100%;"/>
<br/>

The colour signifies the direction of the change: a blue frame means less time was spent in this function in the second profile (UUID4) compared to the first (INT); a red frame means more time was spent in the second profile. The colour intensity indicates the relative change in the number of samples for the frame itself (self time delta).

We can see from the diffgraph that we are spending a lot more time balancing the tree, reading and writing. This is because the unordered nature of UUID4 means they are ordered randomly which is forcing SQLite to constantly re-balance the Btree.

## UUID7

We can theoretically fix this with UUID7 which is time ordered eliminating the ordering problem of UUID4. Let's see if this improves things.

```clojure
(d/q writer
  ["CREATE TABLE IF NOT EXISTS event(id BLOB PRIMARY KEY, data BLOB) WITHOUT ROWID"])

(dotimes [_ 10]
  (time
    (d/with-write-tx [db writer]
      (dotimes [_ 1000000]
        (d/q db ["INSERT INTO event (id, data) values (?, ?)"
                 (random-uuid7-bytes) data])))))
```

Results:

| total rows | time in ms |
| ---------- | ---------- |
|  10000000  | 1372       |
|  20000000  | 1280       |
|  30000000  | 1365       |
|  40000000  | 1250       |
|  50000000  | 1256       |
|  60000000  | 1270       |
|  70000000  | 1246       |
|  80000000  | 1257       |
|  90000000  | 1245       |
| 100000000  | 1258       |

Back to a more reasonable number. Slightly slower than our baseline. UUID blob primary keys are 16 bytes vs int primary keys which are 8 bytes.

## Conclusion

Hopefully, this post helps illustrate some of the pitfalls with UUID primary keys in SQLite and how to navigate them.

The full benchmark code [can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/sqlite-perils-of-uuid).

If you enjoyed this post you might like this one [100000 TPS with SQLite](https://andersmurphy.com/2025/12/02/100000-tps-over-a-billion-rows-the-unreasonable-effectiveness-of-sqlite.html)

## Further reading

- [Clustered Indexes](https://en.wikipedia.org/wiki/Database_index#Clustered)
- [Clustered Indexes and the WITHOUT ROWID Optimization](https://sqlite.org/withoutrowid.html)
- [clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler)
- [Exploring flamegraphs](https://clojure-goes-fast.com/kb/profiling/clj-async-profiler/exploring-flamegraphs/)
- [Diffgraphs](https://clojure-goes-fast.com/kb/profiling/clj-async-profiler/diffgraphs/)

**Thanks to** Everyone on the [Datastar discord](https://discord.gg/bnRNgZjgPh) who read drafts of this and gave me feedback.

### Discussion

- [hackernews](https://andersmurphy.com/2026/06/05/the-perils-of-uuid-primary-keys-in-sqlite.html)
