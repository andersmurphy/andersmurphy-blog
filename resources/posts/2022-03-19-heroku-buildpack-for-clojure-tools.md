Title: Heroku: buildpack for Clojure tools

Over the past 4 years I've had the fortune of working full time in Clojure. The backend for the [Relish](https://apps.apple.com/US/app/id1436692125?mt=8) mobile app is built in Clojure. It runs on [Heroku](https://www.heroku.com/home) and we use [lein](https://leiningen.org/) as our build tool. This has been a great development experience. But, for the next Clojure project I wanted to try [tools.deps](https://clojure.org/guides/deps_and_cli) and [tools.build](https://clojure.org/guides/tools_build). Unfortunately, there isn't an official Heroku buildpack and none of the unofficial alternatives were quite what I was looking for. In the end I decided to roll my own to get comfortable with Heroku's build pack API.

## To use this build pack

Set your heroku app to use this build pack:

```
heroku buildpacks:set https://github.com/andersmurphy/heroku-buildpack-clojure-tools.git -a my-app
```

Make sure your project root contains the following files:

A `Procfile` file:

```
web: java -jar target/my-app.jar -m my-app.app
```

A `bin/build` file (make this file executable with `chmod +x bin/build`):

```
clojure -T:build uber
```

A `deps.edn` file:

```clojure
{:paths   ["src" "resources"]
 :deps    {org.clojure/clojure {:mvn/version "1.11.0-alpha3"}}
 :aliases {:build
           {:deps       {io.github.clojure/tools.build {:git/tag "v0.6.8"
                                                        :git/sha
                                                        "d79ae84"}}
            :ns-default build}}}
```

A `build.clj` file:

```clojure
(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'my-app)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s.jar" (name lib)))

(defn clean [_] (b/delete {:path "target"}))

(defn uber
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
  (b/compile-clj {:basis basis :src-dirs ["src"] :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     basis
           :main      'my-app.app}))
```

For the full repo check out [Heroku buildpack for clojure tools](https://github.com/andersmurphy/heroku-buildpack-clojure-tools).
