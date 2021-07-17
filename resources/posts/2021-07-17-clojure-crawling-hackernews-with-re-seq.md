Title: Clojure: crawling Hacker News with re-seq

Clojure has this nifty function called `re-seq` that returns a lazy sequence of successive matches of a pattern in a string.  This is really useful for turning any string into a list of data. Let's use it to crawl Hacker News!

```Clojure
(defn get-first-8-posts-from-HN []
  (->> (slurp "https://news.ycombinator.com/news?p=1")
       (re-seq #"<td class=\"title\"><a href=\"(.*?)\" class=\"storylink\">(.*?)</a>")
       (map (fn [[_ link title]] title))
       (take 8)))
```

`slurp` to gets the first page of Hacker News. `re-seq` matches on the regex pulling out the `title` of each post.

```Clojure
(get-first-8-posts-from-HN)

=>
("UCSF Launches Translational Psychedelic Research (TrPR) Program"
 "Ethereum London Mainnet Announcement"
 "Show HN: Make spaced-repetition flashcards"
 "Show HN: A low power 1U Raspberry Pi cluster server for inexpensive
  colocation"
 "Last Mile Redis"
 "Windows Print Spooler Elevation of Privilege Vulnerability
  (CVE-2021-34481)"
 "Full Throttle"
 "Google Drive bans distribution of “misleading content”")
```

Amazing we get the top 8 posts of Hacker News!

We can easily extend this function to find posts on Rust in the first five pages of Hacker News.

```Clojure
(defn get-posts-about-rust-from-HN []
  (->> (map #(do
               (Thread/sleep 50)
               (slurp (str "https://news.ycombinator.com/news?p=" %)))
            (range 1 6))
       (apply str)
       (re-seq #"<td class=\"title\"><a href=\"(.*?)\" class=\"storylink\">(.*?)</a>")
       (map (fn [[_ link title]] {:title title :link link}))
       (filter (fn [{:keys [title]}]
                 (re-find #"Rust" title)))))
```

Request the first 5 pages and then filter the results. Simple.

```Clojure
(get-posts-about-rust-from-HN)

=>
({:title "Is Rust Used Safely by Software Developers?",
  :link "https://arxiv.org/abs/2007.00752"})
```

We can even open the first post in our default web browser.

```Clojure
(->> (get-posts-about-rust-from-HN)
     first
     :link
     clojure.java.browse/browse-url)

=>
Opens post in default browser.
```

How's that for automation!

In this post we've seen how to use `slurp` and `re-seq` to write a quick and simple web crawler.
