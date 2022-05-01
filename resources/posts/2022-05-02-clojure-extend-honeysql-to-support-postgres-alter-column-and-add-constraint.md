title:  Clojure: extend honeysql to support postgres alter column and add constraint

[honeysql](https://github.com/seancorfield/honeysql) is an amazing library that lets you write SQL using clojure data structures. This makes SQL much more composable as you no longer need to smash strings together. That being said when using it with [postgresql](https://www.postgresql.org/) there are some features that don't work out of the box. Thankfully, honeysql is very easy to extend. This post will show you how to add support for `alter column` and  `add constraint`.

If you try to use `:modify-column` with postgresql you'll get a PSQLException syntax error:

```clojure
(... {:alter-table   :member
      :modify-column [:owner :text [:not nil]
                     [:default "default"]]} ...)

=>
Execution error (PSQLException) at org.postgresql.core.v3.QueryExecutorImpl/receiveErrorResponse (QueryExecutorImpl.java:2468).
ERROR: syntax error at or near "MODIFY"
  Position: 20
```

If you try to use `:alter-column` (the postgresql syntax for `:modify-column`) you will get a honeysql syntax error:

```clojure
(require '[honey.sql :as hsql])

(hsql/format
 {:alter-table  :task
  :alter-column [:owner :text [:not nil]
                 [:default "default"]]})

=>
Execution error (ExceptionInfo) at honey.sql/format-dsl (sql.cljc:1034).
These SQL clauses are unknown or have nil values: :alter-column
```

We can use the honeysql `format` function to see what sql `:modify-column` generates:

```clojure
(hsql/format
 {:alter-table   :member
  :modify-column [:owner :text [:not nil]
                  [:default "default"]]})

=>
["ALTER TABLE member MODIFY COLUMN owner TEXT NOT NULL DEFAULT 'DEFAULT'"]
```

This is almost identical to the sql we would want to generate for `:alter-column`:

```sql
ALTER TABLE task ALTER COLUMN owner TEXT NOT NULL DEFAULT 'DEFAULT'
```

The `hsql/register-clause!` function is used to register a new clause with honeysql. It takes the name of the new clause, a formatter for the new clause, and which clause it should be called before. In this case we are passing in `:modify-column` as the formatter as we want to re-use the formatter that `:modify-column` uses:

```clojure
(hsql/register-clause! :alter-column :modify-column :modify-column)
```

Once this clause is registered we can generate sql for `:alter-column`:

```clojure
(hsql/format
 {:alter-table  :task
  :alter-column [:owner :text [:not nil]
                 [:default "default"]]})

=>
["ALTER TABLE task ALTER COLUMN owner TEXT NOT NULL DEFAULT 'DEFAULT'"]
```

We can use the same extension mechanism to add support for posgresql `:add-constraint`.

```clojure
(hsql/register-clause! :add-constraint :modify-column :modify-column)

(hsql/format
 {:alter-table    :member
  :add-constraint [:member-account-id-fkey
                   [:foreign-key :account-id]
                   [:references :account :id]]})

=>
["ALTER TABLE member ADD CONSTRAINT member_account_id_fkey FOREIGN KEY(ACCOUNT_ID) REFERENCES ACCOUNT(ID)"]
```

That's all there is to it.

I can't recommend [honeysql](https://github.com/seancorfield/honeysql) enough it makes SQL an absolute joy to work with.
