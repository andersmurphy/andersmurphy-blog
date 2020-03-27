Title: Clojure: code as data

In Clojure, the primary representation of code is the S-expression that takes the form of nested sequences (lists and vectors). The majority of Clojure's functions are designed to operate on sequences. As a result, Clojure code can be manipulated using the same functions that are used to manipulate Clojure data. In other words, there is no distinction between code and data. Code is data. This property is known as [homoiconicity](https://en.wikipedia.org/wiki/Homoiconicity). This article will explore this concept.

### Code equality

We can check two pieces of code for equality by turning the code into data with a `'` symbol and then comparing them with `=`:

```clojure
(= '(map inc items)
   '(map inc items))

=> true

(= '(map inc items)
   '(map dec items))

=> false
```

Simple.

### Code diff

We can use `clojure.set/diff` to find out the difference between these two pieces of code:

```clojure
(clojure.data/diff '(map inc items)
                   '(map dec items))

=> [[nil inc] [nil dec] [map nil items]]
```

From this we can see that the second item in the expression is different. But what happens with nested code?

```clojure
(clojure.data/diff '(when activate?
                      (map dec items))
                   '(map dec items))

=> [[when activate? (map dec items)] [map dec items] nil]
```

Looks like the `diff` function doesn't recognise the commonality between these two pieces of code.

### Commonality with tree-seq

The `tree-seq` function returns a lazy sequence of the nodes in a tree, via a depth-first walk. It takes two functions and the root node of a tree. The first function needs to return `true` if the node is a branch (can have children); in this case we use `coll?` which returns `true` if the node is a collection. The second function will be called on these branch nodes; in this case `seq` which returns a sequence of the children of that node or `nil` if there are none:

```clojure
(tree-seq coll? seq '(when activate?
                       (map dec items)))

=>
((when activate? (map dec items))
 when
 activate?
 (map dec items)
 map
 dec
 items)
```

We can use `tree-seq` to build a sequence of each node for two pieces of code and then compare each node using a `for` comprehension, filtering the results which are equal:

```clojure
(defn find-common-code [code-a code-b]
  (-> (for [a     (tree-seq coll? seq code-a)
            b     (tree-seq coll? seq code-b)
            :when (= a b)]
        a)))

(find-common-code '(when activate?
                     (map dec items))
                  '(map dec items))

=> ((map dec items) map dec items)
```

This returns the common nodes: `(map dec items)`, `map`, `dec` and `items`.

### Refactoring common code

Here we have some of the code used to generate the RSS feed for this blog:

```clojure
(defn generate-feed-items [posts]
  (map (fn [{:keys [post-name date post-path-name]}]
         (let [post-url (str site-url "/" post-path-name)]
           [:item
            [:title post-name]
            [:pubDate (date->rfc822 date)]
            [:link post-url]
            [:guid {:isPermaLink "true"} post-url]]))
       posts))

(defn generate-rss-feed [posts]
  (xml/sexp-as-element
   [:rss
    {:version    "2.0"
     :xmlns:atom "https://www.w3.org/2005/Atom"
     :xmlns:dc   "https://purl.org/dc/elements/1.1/"}
    [:channel
     [:title site-title]
     [:description site-tagline]
     [:link site-url]
     [:atom:link
      {:href site-rss :rel "self" :type "application/rss+xml"}]
     (map (fn [{:keys [post-name date post-path-name]}]
            (let [post-url (str site-url "/" post-path-name)]
              [:item
               [:title post-name]
               [:pubDate (date->rfc822 date)]
               [:link post-url]
               [:guid {:isPermaLink "true"} post-url]]))
          posts)]]))
```

We think some of the code might be duplicated and we want to refactor it out. With what we have just learnt, we can write a function to find the largest piece of common code.

First, we need a function to define the largest piece of code. We decide to use string length as a simple heuristic:

```clojure
(defn code-string-length [code]
    (count (str code)))

(sort-by code-string-length > '((+ 1 2 3) (* 1 200) (map inc [1 2 3 4])))

=> ((map inc [1 2 3 4]) (+ 1 2 3) (* 1 200))
```

The `find-largest-common-code` uses `tree-seq` to get a sequence of nodes and then `frequencies` to find duplicates. We also make sure these duplicates are collections as we aren't interested in duplicates variable names, strings etc. Finally, we order by our string length heuristic and pick the first item:

```clojure
(defn find-largest-common-code [code]
  (->> (tree-seq coll? seq code)
       frequencies
       (keep (fn [[code freq]]
               (when (and (> freq 1) (coll? code))
                 code)))
       (sort-by code-string-length >)
       first))
```

Trying this out on our large piece of code returns the largest duplicate:

```clojure
(find-largest-common-code
   '((defn generate-feed-items [posts]
       (map (fn [{:keys [post-name date post-path-name]}]
              (let [post-url (str site-url "/" post-path-name)]
                [:item
                 [:title post-name]
                 [:pubDate (date->rfc822 date)]
                 [:link post-url]
                 [:guid {:isPermaLink "true"} post-url]]))
            posts))

     (defn generate-rss-feed [posts]
       (xml/sexp-as-element
        [:rss
         {:version    "2.0"
          :xmlns:atom "https://www.w3.org/2005/Atom"
          :xmlns:dc   "https://purl.org/dc/elements/1.1/"}
         [:channel
          [:title site-title]
          [:description site-tagline]
          [:link site-url]
          [:atom:link
           {:href site-rss :rel "self" :type "application/rss+xml"}]
          (map (fn [{:keys [post-name date post-path-name]}]
                 (let [post-url (str site-url "/" post-path-name)]
                   [:item
                    [:title post-name]
                    [:pubDate (date->rfc822 date)]
                    [:link post-url]
                    [:guid {:isPermaLink "true"} post-url]]))
               posts)]]))))

=>
(map
 (fn
  [{:keys [post-name date post-path-name]}]
  (let
   [post-url (str site-url "/" post-path-name)]
   [:item
    [:title post-name]
    [:pubDate (date->rfc822 date)]
    [:link post-url]
    [:guid {:isPermaLink "true"} post-url]]))
 posts)
```

We can then refactor our code accordingly:

```clojure
(defn generate-feed-items [posts]
  (map (fn [{:keys [post-name date post-path-name]}]
         (let [post-url (str site-url "/" post-path-name)]
           [:item
            [:title post-name]
            [:pubDate (date->rfc822 date)]
            [:link post-url]
            [:guid {:isPermaLink "true"} post-url]]))
       posts))

(defn generate-rss-feed [posts]
  (xml/sexp-as-element
   [:rss
    {:version    "2.0"
     :xmlns:atom "https://www.w3.org/2005/Atom"
     :xmlns:dc   "https://purl.org/dc/elements/1.1/"}
    [:channel
     [:title site-title]
     [:description site-tagline]
     [:link site-url]
     [:atom:link
      {:href site-rss :rel "self" :type "application/rss+xml"}]
     (generate-feed-items posts)]]))

```

Much better!

### A macro implementation

If we don't want to pass the code as a quoted list, we can change the `find-largest-common-code` function into a macro:

```clojure
(defmacro find-largest-common-code [& body]
  `(->> (tree-seq coll? seq ~body)
        frequencies
        (keep (fn [[code freq]]
                (when (and (> freq 1) (coll? code))
                  code)))
        (sort-by code-string-length >)
        first))

(find-largest-common-code
 (defn generate-feed-items [posts]
   (map (fn [{:keys [post-name date post-path-name]}]
          (let [post-url (str site-url "/" post-path-name)]
            [:item
             [:title post-name]
             [:pubDate (date->rfc822 date)]
             [:link post-url]
             [:guid {:isPermaLink "true"} post-url]]))
        posts))

 (defn generate-rss-feed [posts]
   (xml/sexp-as-element
    [:rss
     {:version    "2.0"
      :xmlns:atom "https://www.w3.org/2005/Atom"
      :xmlns:dc   "https://purl.org/dc/elements/1.1/"}
     [:channel
      [:title site-title]
      [:description site-tagline]
      [:link site-url]
      [:atom:link
       {:href site-rss :rel "self" :type "application/rss+xml"}]
      (map (fn [{:keys [post-name date post-path-name]}]
             (let [post-url (str site-url "/" post-path-name)]
               [:item
                [:title post-name]
                [:pubDate (date->rfc822 date)]
                [:link post-url]
                [:guid {:isPermaLink "true"} post-url]]))
           posts)]])))

=>
(map
 (fn
  [{:keys [post-name date post-path-name]}]
  (let
   [post-url (str site-url "/" post-path-name)]
   [:item
    [:title post-name]
    [:pubDate (date->rfc822 date)]
    [:link post-url]
    [:guid {:isPermaLink "true"} post-url]]))
 posts)
```

Hopefully, this example helps illustrate the value of code as data (homoiconicity).
