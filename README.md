# andersmurphy-blog

A blog built by generating a static site from markdown files using clojure.

To build content run `(generate-site)` from the repl (see `core.clj`).

To run locally: 

```
clj -M:serve :port 1339 :dir "docs" :headers '{"Cross-Origin-Opener-Policy" "same-origin"}'
```


