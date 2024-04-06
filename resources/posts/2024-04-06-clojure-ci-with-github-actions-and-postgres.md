title: Clojure: CI with Github Actions and Postgres

I recently had to move a project to java 21 for [virtual threads](https://openjdk.org/jeps/444) and [Generational ZGC](https://openjdk.org/jeps/439). Unfortunately, I couldn't find an easy way to use [Circle CI](https://circleci.com/) with Clojure and Java 21. So I figured I'd give [Github Actions](https://github.com/features/actions) a go.

This guide will only cover using Github Actions to run tests, some of which are run against a Postgres database. It also assumes you are using [tools.deps](https://clojure.org/guides/deps_and_cli) and your `deps.edn` looks something like this:

```clojure
{...
 :aliases
 {:dev
 {:extra-paths ["test" "dev"]
 :jvm-opts ["-Duser.timezone=UTC"
            "-XX:+UseZGC"
            "-XX:+ZGenerational"]
  :extra-deps
  {io.github.cognitect-labs/test-runner
   {:git/tag "v0.5.1" :git/sha "dfb30dd"}
   ring/ring-mock {:mvn/version "0.4.0"}}}
  
  :test 
  {:main-opts ["-m" "cognitect.test-runner"]
   :exec-fn   cognitect.test-runner.api/test}

  :migrate-test-db 
  {:main-opts ["-m" "server.test-db"]
   :exec-fn   server.test-db/migate-test-db}}
   ...}
```

We need to create a `.github/workflows/ci.yml` file (the file itself doesn't have to be called `ci.yml` it can be anything you want e.g: `foo.yml`). With the following contents:

```yaml
name: CI

on: [push]

jobs:

  build:
    name: Build
    runs-on: ubuntu-latest
    env:
      TEST_DATABASE_URL: "postgres://username:password@localhost:5432/test-database-name"
      
    services:
      # Label used to access the service container
      postgres:
        # Docker Hub image
        image: postgres
        env:
          POSTGRES_DB: test-database-name
          POSTGRES_USER: username
          POSTGRES_PASSWORD: password
        # Set health checks to wait until postgres has started
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          # Maps tcp port 5432 on service container to the host
          - 5432:5432

    steps:
      - name: Check out repository code
        uses: actions/checkout@v4
        
      - name: Prepare java
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '21'

      - name: Install clojure tools
        uses: DeLaGuardo/setup-clojure@12.5
        with:
          cli: 1.11.2.1446
          cljfmt: 0.10.2
          
      - name: Cache clojure dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.m2/repository
            ~/.gitlibs
            ~/.deps.clj
          # List all files containing dependencies:
          key: cljdeps-${{ hashFiles('deps.edn') }}
          restore-keys: cljdeps-

      - name: Check formatting
        run: cljfmt check

      - name: Run migration on test db
        run: clojure -X:dev:migrate-test-db

      - name: Run tests
        run: clojure -X:dev:test
```

That's basically it. This example doesn't specify a version of Postgres, so you'll want to specify the same version as your production environment. The list of  [docker hub tags can be found here](https://hub.docker.com/_/postgres).

You'll also want your Clojure environment variables that specify db name, username and password (or in this case a connection url):

```yaml
...
    env:
      TEST_DATABASE_URL: "postgres://username:password@localhost:5432/test-database-name"
...
```

To match the db name, username and password specified in the Postgres environment:

```yaml
...
        image: postgres
        env:
          POSTGRES_DB: test-database-name
          POSTGRES_USER: username
          POSTGRES_PASSWORD: password
...
```

That's it. Your are good to go.

But wait! I know what you're thinking. Yuk YAML! Where are all the parentheses?

Well if you have [babashka](https://github.com/babashka/babashka) installed you can live a YAML free existence thanks to [clj-yaml](https://github.com/clj-commons/clj-yaml) (which ironically is a fork of Circle CI's clj-yaml, the same Circle CI we are migrating away from).

clj-yaml is built into babashka, so we can get straight into it.

```clojure
(require '[clj-yaml.core :as yaml])

(defn ->db-url [db-name username password]
  (str "postgres://" username ":" password "@localhost:5432/" db-name))

(defn postgres [db-name username password]
  {:postgres
   {:image "postgres"
    :env
    {:POSTGRES_DB       db-name
     :POSTGRES_USER     username
     :POSTGRES_PASSWORD password}
    :options
    "--health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5"
    :ports ["5432:5432"]}})

(def checkout-code
  {:name "Check out repository code" :uses "actions/checkout@v4"})

(def java
  {:name "Prepare java"
   :uses "actions/setup-java@v4"
   :with {:distribution "zulu" :java-version "21"}})

(def clojure-tools
  {:name "Install clojure tools"
   :uses "DeLaGuardo/setup-clojure@12.5"
   :with {:cli "1.11.2.1446" :cljfmt "0.10.2"}})

(def cache
  {:name "Cache clojure dependencies"
   :uses "actions/cache@v4"
   :with
   {:path         "~/.m2/repository\n~/.gitlibs\n~/.deps.clj\n"
    :key          "cljdeps-${{ hashFiles('deps.edn') }}"
    :restore-keys "cljdeps-"}})

(def check-formatting
  {:name "Check formatting" :run "cljfmt check"})

(def run-migrations
  {:name "Run migration on test db"
   :run  "clojure -X:dev:migrate-test-db"})

(def run-tests
  {:name "Run tests" :run "clojure -X:dev:test"})

(def ci
  (let [db-name  "test-database-name"
        username "username"
        password "password"]
    {:name "CI"
     true  ["push"]
     :jobs
     {:build
      {:name     "Build"
       :runs-on  "ubuntu-latest"
       :env {:TEST_DATABASE_URL
             (->db-url db-name username password)}
       :services (postgres db-name username password)
       :steps
       [checkout-code
        java
        clojure-tools
        cache
        check-formatting
        run-migrations
        run-tests]}}}))

;; Check our generated YAML is semantically the same
;; as our original handwritten YAML. 
(= (yaml/parse-string (slurp "ci.yml"))
   ci)

(spit "generated-ci.yml" (yaml/generate-string ci))
```

Overkill for most projects I imagine. But if you ever end up doing a lot of things with Github Actions, Circle CI or any other YAML heavy interface `clj-yaml` + `babashka` can give you something more composable and manageable. 


