title: Clojure: pruning HTML with clojure.walk

A problem that comes up when web crawling is you get a lot of data that you don't necessarily care about: layout divs, scripts, classes, ids etc. Thankfully, Clojure comes with tools that make removing the data you don't care about straight forward.

We're going to use a library called [hickory](https://github.com/clj-commons/hickory) to parse HTML into [hiccup](https://github.com/weavejester/hiccup) a popular Clojure data representation for HTML that represents elements with vectors and attributes with maps.

*Note: hickory comes with CSS-style selectors that operate on hickory-format data. These selectors work well. However, they have their own custom interface and have to be [combined with zippers to prune data](https://github.com/clj-commons/hickory/issues/41). clojure.walk is more generic and lets us combine selection with modification more easily.*

Let's add hickory and slurp some HTML data:

```clojure
(clojure.repl.deps/add-lib
  'org.clj-commons/hickory {:mvn/version "0.7.4"})

(require '[hickory.core :as hick])

(def html-data
  (-> (slurp "https://clojure.org/reference/clojure_cli")
    hick/parse hick/as-hiccup))
```

As we see below the data contains lots of information we are not interested in. Like comments, scripts, meta data etc.

```clojure
html-data

=>
("<!DOCTYPE html>"
 "\n"
 "<!-- This site was created in Webflow. http://www.webflow.com-->"
 "\n"
 "<!-- Last Published: Fri Nov 13 2015 01:48:45 GMT+0000 (UTC) -->"
 "\n"
 [:html ...
  [:head
   {}
   "\n  "
   [:meta {:charset "utf-8"}] ... )
```

We are going to add two libraries that will make the processing of this data easier to describe (interestingly both these libraries use a hiccup like syntax).  [Malli](https://github.com/metosin/malli) lets validate data (it can do much more, but that's what we will be using it for). [Regal](https://github.com/lambdaisland/regal) is a regex builder that gives us a more composable and more readable way of writing regular expressions (similar to [Emacs rx notation](https://www.gnu.org/software/emacs/manual/html_node/elisp/Rx-Notation.html)). 

```clojure
(clojure.repl.deps/add-libs
  '{metosin/malli      {:mvn/version "0.14.0"}
    lambdaisland/regal {:mvn/version "0.0.143"}})

(require '[malli.core :as m])
(require '[lambdaisland.regal :refer [regex]])
(require '[clojure.walk :as walk])
```

The code below defines some "patterns" for the tags and strings we want to remove.

*Note: A quirk of Malli's `:re` implementation is it uses `re-find` not `re-matches` so we need to specify `:start`/`:end` in `blank-re` or we will match on strings that contain whitespace and other content, not just whitespace.*

```clojure
(def blank-re    [:cat :start [:* :whitespace] :end])
(def comment-re  [:cat "<!--" [:* :any] "-->"])
(def doc-type-re "<!DOCTYPE html>")

(def strings-to-remove
  (regex [:alt blank-re comment-re doc-type-re]))

(defn tags-to-remove [tags]
  [:or
   ;; elements that match tags we don't want
   [:cat [:fn tags] [:* :any]]
   ;; elements that only have two children and are not :br
   [:cat [:not [:fn #{:br}]] :any]
   ;; strings we don't want
   [:and :string [:re strings-to-remove]]
   ;; this removes anything we don't expect like jsoup objects etc
   [:not [:or :keyword :string [:vector :any] :map]]])
```

Once these are defined we can use `walk/postwalk` to remove any vectors that contain tags we don't want. It's worth pointing out that it is not enough to check a node is a `vector?` as `clojure.walk` traverses maps as map entries, which are vectors. So we need to explicitly check `(not (map-entry %))`, otherwise we will end up treating map entries as hiccup vectors.

```clojure
(defn remove-tags [tags hiccup]
  (let [remove-tag? (m/validator (tags-to-remove tags))]
    (walk/postwalk
      #(cond (and (vector? %) (not (map-entry? %)))
             (into [] (remove remove-tag?) %)
             :else %)
      (vec hiccup))))
```

Let's try `remove-tags` out.

```clojure
(->> html-data
  (remove-tags #{:head :script :style :nav :link}))

=>
[[:html
  {:lang "en",
   :data-wf-site "56414d6fc8c27cad0f4e12e7",
   :data-wf-page "5643ac587b1f28dc58ed6b89"}
  [:body
   {}
   [:div ... ] ...] ...] ...]
```

The output is cleaner but there's still a bunch of unnecessary nesting, particularly `:div` elements. It would be great to unwrap these where we can. First we write a pattern to validate whether an element is something we want to unwrap.

```clojure
(def tags-to-unwrap
  [:or
    ;; Elements that only have one child
    [:cat [:fn #{:div :span :article :main :body :html}]
      :any [:or [:* :any] :string]]
    ;; Vector with single child that is also a vector
    [:cat [:vector :any]]])
```

This is easy to add to our `remove-tags` function. It replaces the surrounding element with its only child when it satisfies  `tags-to-unwrap`.

```clojure
(defn remove-tags [tags hiccup]
  (let [remove-tag? (m/validator (tags-to-remove tags))
+       unwrap-tag? (m/validator tags-to-unwrap)]
    (walk/postwalk
      #(cond (and (vector? %) (not (map-entry? %)))
             (let [el (into [] (remove remove-tag?) %)]
+               (if (unwrap-tag? el) (peek el) el))
             :else %)
      (vec hiccup))))
```

Our HTML data is much cleaner, but we still have a load of attribute data we don't care about.

```clojure
(->> html-data
(remove-tags #{:head :script :style :nav :link}))

=>
[:body
 {}
 [:a
  {:href "/index", :class "w-nav-brand w-clearfix clj-logo-container"}
  "Clojure"] ... ]
```

In practice for my use case. I only care about the `:href` attribute. So we add a clause that when `walk/postwalk` encounters a map we select only the `:href` key and value.

```clojure
(defn remove-tags [tags hiccup]
  (let [remove-tag? (m/validator (tags-to-remove tags))
        unwrap-tag? (m/validator tags-to-unwrap)]
    (walk/postwalk
      #(cond (and (vector? %) (not (map-entry? %)))
             (let [el (into [] (remove remove-tag?) %)]
               (if (unwrap-tag? el) (peek el) el))
+            (map? %) (select-keys % [:href])
             :else    %)
      (vec hiccup))))
```

Perfect, we now only have the data we care about.

```clojure
(->> html-data
  (remove-tags #{:head :script :style :nav :link}))

=>
[:body
 {}
 [:a {:href "/index"} "Clojure"] ... ]
```

That's as far as we'll go in this post.

The full example [project can be found here](https://github.com/andersmurphy/clj-cookbook/tree/master/crawling/pruning-html-with-clojure-walk).

There's definitely more you can do to sanitise HTML data. Currently our implementation relies on semantic data. If you are interested in a more probabilistic approach I recommend checking out the source code of [readablility.js](https://github.com/mozilla/readability) which is a standalone version of the readability library used for Firefox Reader View.
