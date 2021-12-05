(defproject andersmurphy-blog "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.11.0-alpha3"]
                 [org.clojure/data.xml "0.0.8"]
                 [hiccup "1.0.5"]
                 [markdown-clj "1.10.7"]]
  :aliases {"generate-site" ["run" "-m" "core/generate-site"]})
