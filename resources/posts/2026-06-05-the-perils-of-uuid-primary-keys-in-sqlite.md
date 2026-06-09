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

SQLite also supports WITHOUT ROWID tables. These tables have no implicit rowid. Instead, the primary key you declare becomes the clustered index. Why would you use without rowid? Simply, because it avoids you having a rowid index and a primary key index. This causes write amplification. But, also an additional lookup even when using the primary key as the rowid needs to be used to retrieve the actual data (unless the index is covering).

*Note: In SQLite rowid tables are implemented as B+-Trees where all content is stored in the leaves of the tree, whereas WITHOUT ROWID tables are implemented using ordinary B-Trees with content stored on both leaves and intermediate nodes.*

## Baseline

Let's establish a performance baseline with regular rowid int primary key. We'll insert 10 million rows in batches of 1 million. 

```clojure
(d/q writer
  ["CREATE TABLE IF NOT EXISTS event(id INTEGER PRIMARY KEY, data BLOB)"])
      
(dotimes [_ 10]
  (time
    (d/with-write-tx [db writer]
      (dotimes [_ 1000000]
        (d/q db ["INSERT INTO event (data) values (?)" data])))))
```

Results:

| total rows | time in ms |
| ---------- | ---------- |
|  1000000   | 838        |
|  2000000   | 762        |
|  3000000   | 819        |
|  4000000   | 713        |
|  5000000   | 721        |
|  6000000   | 757        |
|  7000000   | 692        |
|  8000000   | 702        |
|  9000000   | 696        |
| 10000000   | 715        |

Roughly a million inserts per second.

## UUID4 WITHOUT ROW ID

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
| 1000000    | 2649       |
| 2000000    | 5644       |
| 3000000    | 7137       |
| 4000000    | 8352       |
| 5000000    | 9359       |
| 6000000    | 9817       |
| 7000000    | 10490      |
| 8000000    | 11130      |
| 9000000    | 11668      |
| 10000000   | 12586      |

Oh no! What's happened here the inserts are 14-16x slower?!

## Profile

That's a big difference. But, lets not guess when we can profile.

Below is a normalised diffgraph. A diffgraph compares two profiling snapshots (in this case INTEGER vs UUID4) and displays the differences in a flamegraph structure. Unlike a regular diffgraph that shows absolute changes, a normalised view adjusts the total number of samples between the two compared profiles to be the same. This means we can see the relative differences as a percentage. This matters because our profiles will run for different amounts of time.

<img src="/assets/diffgraph_perils.png" alt="diffgraph" style="width: 100%;"/>
<br/>

The colour signifies the direction of the change: a blue frame means less time was spent in this function in the second profile (UUID4) compared to the first (INTEGER); a red frame means more time was spent in the second profile. The colour intensity indicates the relative change in the number of samples for the frame itself (self time delta).

We can see from the diffgraph that we are spending a lot more time balancing the tree, reading and writing. This is because the unordered nature of UUID4 means they are ordered randomly which is forcing SQLite to constantly re-balance the Btree.

## UUID7 WITHOUT ROWID

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
|  1000000   | 1372       |
|  2000000   | 1280       |
|  3000000   | 1365       |
|  4000000   | 1250       |
|  5000000   | 1256       |
|  6000000   | 1270       |
|  7000000   | 1246       |
|  8000000   | 1257       |
|  9000000   | 1245       |
| 10000000   | 1258       |

Back to a more reasonable number. Still slower than our baseline. UUID blob primary keys are 16 bytes vs int primary keys which are 8 bytes.

## UUID4 WITH ROWID

Now lets try UUID4 WITH ROWID. This means the hidden rowid will be the clustered index. The upside of this is rowid is sequential. The downside is that the table now has two indexes so there will be write amplification.

```clojure
(d/q writer
  ["CREATE TABLE IF NOT EXISTS event(id BLOB PRIMARY KEY, data BLOB)"])

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
| 1000000    | 2003       |
| 2000000    | 2324       |
| 3000000    | 3285       |
| 4000000    | 4399       |
| 5000000    | 5194       |
| 6000000    | 5659       |
| 7000000    | 6215       |
| 8000000    | 6467       |
| 9000000    | 6924       |
| 10000000   | 7119       |

This doesn't perform as well as UUID7 WITHOUT ROWID partly because you still building an index with random insertions (even though it's not the clustered index).

## Conclusion

Hopefully, this post helps illustrate some of the pitfalls with UUID primary keys in SQLite and how to navigate them.

The full benchmark code [can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/sqlite-perils-of-uuid).

If you enjoyed this post you might like this one [100000 TPS with SQLite](https://andersmurphy.com/2025/12/02/100000-tps-over-a-billion-rows-the-unreasonable-effectiveness-of-sqlite.html).

There's also a subsequent [article on pre-sorting ids to improve insert performance](https://andersmurphy.com/2026/06/07/sqlite-improving-performance-with-pre-sort.html).

## Further reading

- [Clustered Indexes](https://en.wikipedia.org/wiki/Database_index#Clustered)
- [Clustered Indexes and the WITHOUT ROWID Optimization](https://sqlite.org/withoutrowid.html)
- [clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler)
- [Exploring flamegraphs](https://clojure-goes-fast.com/kb/profiling/clj-async-profiler/exploring-flamegraphs/)
- [Diffgraphs](https://clojure-goes-fast.com/kb/profiling/clj-async-profiler/diffgraphs/)

**Thanks to** Everyone on the [Datastar discord](https://discord.gg/bnRNgZjgPh) who read drafts of this and gave me feedback.

### Discussion
- [hackernews](https://news.ycombinator.com/item?id=48419571)
- [reddit](https://www.reddit.com/r/programming/comments/1tyalr6/the_perils_of_uuid_primary_keys_in_sqlite/)
- [lobsters](https://lobste.rs/s/76plqm/perils_uuid_primary_keys_sqlite)
p
