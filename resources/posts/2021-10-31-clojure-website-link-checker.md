Title: Clojure: website link checker

Writing a simple website link checker in Clojure for fun and profit. Clojure has this nifty function called `re-seq` that returns a lazy sequence of successive matches of a pattern in a string we can combine this with recursion to write a primitive website link checker.

First we write a function to find all the links on a given set of pages for a domain.

```Clojure
(defn get-all-links-on-pages [domain pages]
  (->> (filter #(clojure.string/includes? % domain) pages)
       (mapcat (fn [page]
                 (->> (try (slurp page) (catch Exception e ""))
                      (re-seq #"href=[\"'](.*?)[\"']")
                      (map second))))))
```

`(filter #(clojure.string/includes? % domain) pages)` prevents us slurping pages outside our domain (so that we don't end up crawling the whole internet). Because this function is only for gathering all the links on a page we don't care if a `slurp` fails so we guard against this with a `try ... catch`.

Next we write a recursive function to visit all links on a domain and continue following links within that domain until all have been `seen`.

```Clojure
(defn get-all-links-on-domain
  ([domain] (get-all-links-on-domain domain #{} [domain]))
  ([domain seen links]
   (if (seq links)
     (let [seen (into seen links)]
       (->> (get-all-links-on-pages domain links)
            (remove seen)
            (recur domain seen)))
     seen)))
```

If we call this function we get a set of all the links on this domain (note this won't find orphaned pages).

```Clojure
(get-all-links-on-domain "https://andersmurphy.com")

=>
#{"https://github.com/andersmurphy/clj-cookbook/tree/master/generating-files/html-and-xml-example"
  "https://andersmurphy.com/2020/08/20/emacs-setting-up-apheleia-to-use-zprint.html"
  "http://gallium.inria.fr/~huet/PUBLIC/zip.pdf"
  "https://en.wikipedia.org/wiki/Open%E2%80%93closed_principle"
  "https://brew.sh/"
  ...}
```

This function takes just under 2 seconds to find all 149 links on this website.

```Clojure
(time (count (get-all-links-on-domain "https://andersmurphy.com")))

=>
"Elapsed time: 1753.119164 msecs"

149
```

Not particularly slow, but we can make it faster by using `pmap`.

```Clojure
(defn get-all-links-on-pages [domain pages]
  (->> (filter #(clojure.string/includes? % domain) pages)
       (pmap (fn [page]
               (->> (try (slurp page) (catch Exception e ""))
                    (re-seq #"href=[\"'](.*?)[\"']")
                    (map second))))
       flatten))
```

This is a simple way of processing each link in parallel.

```Clojure
(time (count (get-all-links-on-domain "https://andersmurphy.com")))

=>
"Elapsed time: 873.775003 msecs"

149
```

To check the links we write a function that connects to the url and checks the response code (that way we don't do any additional processing of the data).

```Clojure
(defn check-link [link]
  (when-not (= (try (-> (clojure.java.io/as-url link)
                        .openConnection
                        .getResponseCode)
                    (catch Exception e 404))
               200)
    link))
```

Weaving all this together we get a primitive program for checking links on a domain.

```Clojure
(->> (map check-link (get-all-links-on-domain "https://andersmurphy.com"))
       (remove nil?))

=>

("https://andersmurphy.com/2017/12/28/desert-island-code-reduce-map-and-filter/"
 "https://github.com/andersmurphy/chain/commit/1afec87e14f609bd5c7deb6aff8c5a00774be92b"
 "https://github.com/clojure/math.combinatoricsh/"
 "http://proguard.sourceforge.net/"
 "https://en.wikipedia.org/wiki/Recursion_(computer_science"
 "https://github.com/andersmurphy/chain/commit/9d2241a2a6d2571696a1d3ad5ba37e521d8641f5"
 "https://uk.linkedin.com/in/anders-murphy-76457b3a"
 "https://proguard.sourceforge.net/"
 "https://github.com/andersmurphy/chain/commit/4462327da5849f6ac7c4a41e290d84dc6f016b21"
 "https://github.com/andersmurphy/chain/commit/531597724d68cf27d6e9fdd2e88f54fe4082c841")
```

Turns out there were broken links on this website!

This program is pretty slow and takes 40s to run. This is partly due to some of the external links having long load times.

```Clojure
(time
 (->> (map check-link (get-all-links-on-domain "https://andersmurphy.com"))
      (remove nil?)
      doall))
=>
"Elapsed time: 39476.81269 msecs"

...
```

Again using `pmap` we can get some quick performance gains. Reducing our run time to 10s.

```Clojure
(time
 (->> (pmap
       check-link
       (get-all-links-on-domain "https://andersmurphy.com"))
      (remove nil?)
      doall))

=>
"Elapsed time: 10484.143708 msecs"

...
```

There are plenty of ways to improve the performance of this program. For example currently it performs two passes: gathering all the links and then checking them. There are also functional improvements like handling relative links etc. But they will be left as an exercise for the reader.

In this post we've seen how to use `slurp`, `re-seq`, `pmap` and recursion to write a basic link checker.
