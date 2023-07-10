Title: SQLite: building from source on macOS

I've been playing around with [Project Panama](https://openjdk.org/projects/panama/) to explore building a simpler and lighter SQLite driver to use from Clojure. This required
downloading and compiling SQlite from source for macOS/OS X.

Turns out this is increadibly straightforward!

## 1. Download SQLite source

Download the latest [SQLite C source code as an amalgamation](https://www.sqlite.org/download.html).o

Unzip the amalgamation.zip:

```
unzip sqlite-amalgamation-3420000.zip
```

## 2. Compile SQLite

Navigate into the unzipped folder:

```
cd  sqlite-amalgamation-3420000
```

Compile the `sqlite3.c` file using `gcc`: 

```
gcc shell.c sqlite3.c -lpthread -ldl -lm -o sqlite3
```

## 3. Profit

You'll find the `sqlite3` executable in the directory where you ran gcc (in out case the unzipped folder). 

Done.
