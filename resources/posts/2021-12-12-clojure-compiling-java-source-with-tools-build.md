Title: Clojure: compiling java source with tools.build

Recently I stumbled over an old Java project from 2011. I wanted to see if I could get it to run. However, the original program had a bunch of IDE related build files that I no longer understood. What if I used Clojure to build the project? The fruit of that journey is covered in this blog post.

Lets start with a top level overview of the project structure.

```
├── README.md
├── build.clj
├── deps.edn
├── java
│   └── greatings
│       └── Greater.java
└── src
    └── compiling_java
        └── core.clj
```

## Java code

The Java source lives in the  `java` directory.

```Java
package greatings;

public class Greater {
  public void great() {
      System.out.println("Hello, world!");
  }
}
```

## Clojure code

The Clojure source lives in `src`.

```Clojure
(ns compiling-java.core
  (:gen-class)
  (:import [greatings Greater]))

(defn -main []
  (.great (Greater.)))
```

Worth pointing out  `(:gen-class)` this will be important later when it comes to building an uberjar. When compiling, this generates .class file with a given package-qualified name. This will ensure we can reference this class as the main entry point for our uberjar.

## deps.edn

We will be using `tools.build` to compile our java code. To do this we need to add it as a dependency.

```Clojure
{:paths ["src"]
 :deps {org.clojure/clojure {:mvn/version "1.10.3"}}
 :aliases
 {:build {:deps {io.github.clojure/tools.build
                 {:git/tag "v0.6.8"
                  :git/sha "d79ae84"}}
          :ns-default build}
  :dev {:paths ["src" "target/classes"]}}}
```

We add `target/classes` as a `:dev` dependency so that when using the repl the Java classes will be available.

## Compiling Java with tools.build

`build.clj` is where we define our build tasks. I've added a `jcompile` task that will compile our Java source code.

```Clojure
(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'compiling-java)
(def version "0.1.1")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jcompile [_]
  (clean nil)
  (b/javac {:src-dirs ["java"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["-source" "8" "-target" "8"]}))
```

This task can be run with:

```Bash
clj -T:build jcompile
```

## Calling our java code at the relp

Start the repl using the deb profile:

```
clj -M:dev
```

Load the namespace and test the Java code:

```Clojure
(require 'compiling-java.core)
(in-ns 'compiling-java.core)

(.great (Greater.))

=>
Hello, world!

nil
```

Everything is working as expected.

## Building and running an uberjar

Clojure/Java projects are often shipped/deployed as an uberjar. An uberjar is the program and all its dependencies compiled into a single executable. With `tools.build` making uberjars is trivial.

First we add the following uber task to our `build.clj` file:

```Clojure
(def uber-file (format "target/%s-%s.jar" (name lib) version))

(defn uber [_]
  (clean nil)
  (jcompile nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'compiling-java.core}))
```

This task can be run with:

```Bash
clj -T:build uber
```

The resulting uberjar file can be run with:

```Bash
java -jar target/compiling-java-0.1.1.jar

=>
Hello, world!
```

That's all there is to it.

`tools.build` is really simple to use. It's philosophy, that the project build is inherently a program, is really powerful. I'll be using it as my build tool for both Clojure and Java projects going forward.

The full example [project can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/compiling-java).
