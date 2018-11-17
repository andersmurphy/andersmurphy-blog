(defproject hellorelish-articles "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.0-beta5"]
                 [cheshire "5.8.0"]
                 [hiccup "1.0.5"]
                 [markdown-clj "1.0.2"]]
  :aliases {"generate-site" ["run" "-m" "core/generate-site"]})
