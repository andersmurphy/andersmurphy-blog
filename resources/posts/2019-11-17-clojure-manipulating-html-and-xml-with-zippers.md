Title: Clojure: manipulating HTML and XML with zippers

Clojure provides a powerful namespace for manipulating HTML/XML called
`clojure.zip`. It uses the concept of functional zipper (see [Functional Pearls The zipper](http://gallium.inria.fr/~huet/PUBLIC/zip.pdf)) to make manipulating hierarchical data structures simple and efficient. This article will cover how to use zippers to manipulate HTML/XML in Clojure.

## A quick overview of zippers

Lets start by requiring the `clojure.xml` for parsing XML and `clojure.zip` for zipper functions.

```clojure
(ns manipulating-html-and-xml-example.core
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]))
```

We have an XML string.

```clojure
(def xml-string
  "<item>
    <title>Foo</title>
   </item>")
```

We parse it and get a representation as a nested map.

```clojure
(defn parse-xml-string [s]
  (xml/parse (java.io.ByteArrayInputStream. (.getBytes s))))

(parse-xml-string xml-string)

=>
{:tag :item,
 :attrs nil,
 :content [{:tag :title, :attrs nil, :content ["Foo"]}]}
```

If we convert the nested map representation into a zipper we get a two element vector with the first element being our original data and the second element being `nil`.

```clojure
(-> (parse-xml-string xml-string)
    (zip/xml-zip))

=>
[{:tag :item,
  :attrs nil,
  :content [{:tag :title, :attrs nil, :content ["Foo"]}]}
 nil]
```

Calling `zip/next` takes us to the next location in the zipper. The first element in the vector is the current node. The second element in the vector contains a map that represents a path with the following keys:

- `:l` a list of sibling nodes to the left of the current node.
- `:pnodes` a list of parent nodes.
- `:ppath` path to the parent node.
- `:r` a list of sibling nodes to the right of the current node.

`:ppath` is `nil` for now because the parent node is the root of the tree.

```clojure
(-> (parse-xml-string xml-string)
    zip/xml-zip
    zip/next)

=>
[{:tag :title, :attrs nil, :content ["Foo"]}
 {:l [],
  :pnodes
  [{:tag :item,
    :attrs nil,
    :content [{:tag :title, :attrs nil, :content ["Foo"]}]}],
  :ppath nil,
  :r nil}]
```

After calling `zip/next` again `:ppath` now contains a path.

```clojure
(-> (parse-xml-string xml-string)
    zip/xml-zip
    zip/next
    zip/next)

=>
["Foo"
 {:l [],
  :pnodes
  [{:tag :item,
    :attrs nil,
    :content [{:tag :title, :attrs nil, :content ["Foo"]}]}
   {:tag :title, :attrs nil, :content ["Foo"]}],
  :ppath
  {:l [],
   :pnodes
   [{:tag :item,
     :attrs nil,
     :content [{:tag :title, :attrs nil, :content ["Foo"]}]}],
   :ppath nil,
   :r nil},
  :r nil}]
```

In summary, zippers are a location which is a two element vector that consists of a node and a path. What makes zipper so compelling is that `clojure.zip` comes with a collection of functions for performing common operations on them like navigation and editing (we've already seen `zip/xml-zip` and `zip/next`). Zippers also let us iterate rather than recur over a tree which has practical applications (like avoiding stack overflow errors for deeply nested trees).

## Putting zippers to work

In this example we will scrape an RSS feed, generate some HTML and then inject it into an existing HTML page replacing part of the original content.

We will use `xml/parse` to parse the RSS feed of this blog.

```clojure
(def xml-feed (xml/parse "https://andersmurphy.com/feed.xml"))
```

We are interested in the `item` tag but can't quite remember the structure of `feed.xml`. We could look at the `feed.xml` file to work out how deep in the hierarchical data the items are but destructuring extremely nested data can be quite cumbersome. Instead we can use a zipper to perform a depth first traversal of the entire document visiting every node and then filter the tags we care about.

```clojure
(->> (zip/xml-zip xml-feed)
     (iterate zip/next)
     (take-while (complement zip/end?))
     (map zip/node)
     (filter (fn [node] (and (associative? node)
                             (= (:tag node) :item)))))
```

First the XML is turned into a zipper with `zip/xml-zip`, we then generate a sequence of all the locations in the zipper with `(iterate zip/next)` and `(take-while (compliment zip/end?))`. `zip/next` goes to the next location from the current location and `zip/end?` returns true when we are at the end of our depth first walk. We convert that list of locations into nodes with  `(map zip/node)` and then filter all the nodes with the `item` tag returning a list of items.

```clojure

({:tag :item,
  :attrs nil,
  :content
  [{:tag :title,
    :attrs nil,
    :content ["Advantages of an Android free zone"]}
   {:tag :pubDate,
    :attrs nil,
    :content ["Thu, 27 Aug 2015 00:00:00 GMT"]}
   {:tag :link,
    :attrs nil,
    :content
    ["https://andersmurphy.com/2015/08/27/advantages-of-an-android-free-zone.html"]}
   {:tag :guid,
    :attrs {:isPermaLink "true"},
    :content
    ["https://andersmurphy.com/2015/08/27/advantages-of-an-android-free-zone.html"]}]}
    ...)
```

Which we then transform into a hiccup HTML representation.

```clojure
(->> items
     (map :content)
     (map (fn [[{[title] :content}
                {[date] :content}
                {[link] :content}]]
            [:div
             [:h1 title]
             [:p date]
             [:a {:href link} link]])))

=>
([:div
  [:h1 "Advantages of an Android free zone"]
  [:p "Thu, 27 Aug 2015 00:00:00 GMT"]
  [:a
   {:href
    "https://andersmurphy.com/2015/08/27/advantages-of-an-android-free-zone.html"}
   "https://andersmurphy.com/2015/08/27/advantages-of-an-android-free-zone.html"]]
   ...)
```

We want to inject this HTML list into an existing HTML page. So we need to get an existing HTML page and then write a function to select the node we want.

```clojure
(def html-page (slurp "https://andersmurphy.com/"))

(defn zip-select-first [loc tag pred]
  (when-not (zip/end? loc)
    (if (some
         (every-pred associative?
                     #(some-> % tag pred))
         (zip/node loc))
      loc
      (recur (zip/next loc) tag pred))))
```

`zip-select-first` does a depth first traversal of a zipper and finds the first node that is associative and has a tag that satisfies a predicate. `every-pred` is a handy higher order function that returns a function that returns true if a value satisfies all it's predicates. `some->` is like `->` except that is short circuits if a function returns `nil`.

For the last part of this pipeline we need to add some more dependencies: `hiccup.core` for writing html, `hickory.core` for reading html, and `hickory.zip` for creating zippers for html.

```clojure
(ns manipulating-html-and-xml-example.core
  (:require [hiccup.core :as hiccup]
            [hickory.core :as hick]
            [clojure.xml :as xml]
            [hickory.zip :as hick-zip]
            [clojure.zip :as zip]))
```

Putting it all together. We read the XML feed, filter the items we care about, convert them to hiccup, find the first `:div` element with it's `:class` tag equal to `"content container"` and replace it with our own `:div` element. Finally we persist our changes with `zip/root`, convert the hiccup to HTML and write it to a file.

```clojure
(defn build-page []
  (let [content (xml-feed->hiccup xml-feed)]
    (spit "page.html"
          (-> html-page
              hick/parse
              hick/as-hiccup
              hick-zip/hiccup-zip
              (zip-select-first :class #(= % "content container"))
              (zip/replace [:div {:class "content container"} content])
              zip/root
              hiccup/html))))
```

This concludes this guide to manipulating HTML/XML in Clojure. The full example project can be found [here](https://github.com/andersmurphy/clj-cookbook/tree/master/zippers/manipulating-html-and-xml-example).
