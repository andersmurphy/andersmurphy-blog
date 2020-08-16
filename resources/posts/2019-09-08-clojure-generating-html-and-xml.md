Title: Clojure: generating HTML and XML

HTML and XML are ubiquitous, whether it's the pages of a static site or configuration for a logger, being able to programmatically generate these files can be really powerful. This article will cover how to generate HTML and XML files in Clojure.

## HTML

In some languages you use template ([ERB](https://ruby-doc.org/stdlib-2.6.4/libdoc/erb/rdoc/ERB.html)) to generate HTML/XML or a language syntax extension ([JSX](https://reactjs.org/docs/introducing-jsx.html)). Inevitably, as you need to solve more and more interesting problems, the limitations of these template/extension languages become an issue. Eventually, you either live with those limitations or the template/extension language ends up being a super set of the language they are extending. Effectively, the templating language implements the underlying language in addition to it's own syntax, often with some awkward differences.

Clojure takes a different approach, rather than implementing a new embedded syntax it represents HTML and XML with Clojure data structures (vectors and maps). This gives you all the power of Clojure for manipulating and building HTML and XML files. This is possible as Lisp and HTML/XML are both trees that represent data. For a more in depth discussion on the similarities between Lisp and XML check out [this](https://www.defmacro.org/ramblings/lisp.html) article.

For generating HTML we are going to use Hiccup. Hiccup is a library for representing HTML in Clojure. It uses vectors to represent elements, and maps to represent an element's attributes.

Add `hiccup` as a dependency in the project `deps.edn` file.

```clojure
{:deps {hiccup {:mvn/version "1.0.5"}}}
```

Require `hiccup`.

```clojure
(ns html-and-xml-example.core
  (:require [hiccup.core :as html]))
```

The `html` function creates an HTML string representation of a 404 error page which we then `spit` into a file. Vectors are used to represent elements, keywords represent tags, and maps represent attributes. Interestingly this data representation is less verbose that HTML as closing tags are omitted. This example also illustrates the use of a variable `site-url` in the HTML data.

```clojure
(def site-url "https://andersmurphy.com")

(defn generate-404-html []
  (html/html [:html
              [:body
               [:h1 {:class "post-title"} "404: Page not found"]
               [:p "Sorry, we've misplaced that URL or it's
                 pointing to something that doesn't exist."
                [:a {:href site-url} "Head back home"]
                " to try finding it again."]]]))

(defn write-404! [html]
  (let [path-name "404.html"]
    (spit path-name html)))

(comment (-> (generate-404-html)
             write-404!))
```

The generated output file `404.html` has the following content. Note that I have formatted the output for this blog post to make it human readable, the actual output is a single line without any white space.

```html
<html>
 <body>
  <h1 class="post-title">404: Page not found</h1>
   <p>Sorry, we've misplaced that URL or it's
pointing to something that doesn't exist.<a href="https://andersmurphy.com">Head back home</a> to try finding it again.
  </p>
 </body>
</html>
```

## XML

For generating XML we are going to use Clojure.data.xml. Clojure.data.xml is a library for representing XML in Clojure. It uses vectors to represent elements, and maps to represent an element's attributes (identical to Hiccup).

Add `clojure.data.xml` as a dependency in the project `deps.edn` file.

```clojure
{:deps {hiccup               {:mvn/version "1.0.5"}
        org.clojure/data.xml {:mvn/version "0.0.8"}}}
```

Require `clojure.data.xml`.

```clojure
(ns html-and-xml-example.core
  (:require [hiccup.core :as html]
            [clojure.data.xml :as xml]))
```

The `sexp-as-element` function is used to generate an XML RSS feed. `map` iterates over a sequence of posts and generates the corresponding RSS items. There's nothing special about the syntax in this example, it's just regular Clojure. Finally, `emit` writes the XML to file.

```clojure
(def site-title "Site Title")
(def site-rss (str site-url "/feed.xml"))
(def site-description "Site Description")

(defn generate-rss-xml [posts]
  (xml/sexp-as-element
   [:rss
    {:version    "2.0"
     :xmlns:atom "https://www.w3.org/2005/Atom"
     :xmlns:dc   "https://purl.org/dc/elements/1.1/"}
    [:channel
     [:title site-title]
     [:description site-description]
     [:link site-url]
     [:atom:link
      {:href site-rss :rel "self" :type "application/rss+xml"}]
     (map (fn [{:keys [post-name date post-path-name]}]
            (let [post-url (str site-url "/" post-path-name)]
              [:item
               [:title post-name]
               [:pubDate date]
               [:link post-url]
               [:guid {:isPermaLink "true"} post-url]]))
          posts)]]))

(def posts [{:post-name      "Foo"
             :post-path-name "foo"
             :date           "Fri, 6 Sep 2019 00:00:00 GMT"}
            {:post-name      "Bar"
             :post-path-name "bar"
             :date           "Sat, 7 Sep 2019 00:00:00 GMT"}
            {:post-name      "Baz"
             :post-path-name "baz"
             :date           "Sun, 8 Sep 2019 00:00:00 GMT"}])

(defn write-rss! [xml]
  (with-open [out-file (java.io.FileWriter. "feed.xml")]
    (xml/emit xml out-file)))

(comment (-> (generate-rss-xml posts)
             write-rss!))
```

The generated output file `feed.xml` has the following content. Note that I have formatted the output for this blog post to make it human readable, the actual output is a single line without any white space.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss xmlns:atom="https://www.w3.org/2005/Atom" xmlns:dc="https://purl.org/dc/elements/1.1/" version="2.0">
   <channel>
      <title>Site Title</title>
      <description>Site Description</description>
      <link>https://andersmurphy.com</link>
      <atom:link href="https://andersmurphy.com/feed.xml" rel="self" type="application/rss+xml" />
      <item>
         <title>Foo</title>
         <pubDate>Fri, 6 Sep 2019 00:00:00 GMT</pubDate>
         <link>https://andersmurphy.com/foo</link>
         <guid isPermaLink="true">https://andersmurphy.com/foo</guid>
      </item>
      <item>
         <title>Bar</title>
         <pubDate>Sat, 7 Sep 2019 00:00:00 GMT</pubDate>
         <link>https://andersmurphy.com/bar</link>
         <guid isPermaLink="true">https://andersmurphy.com/bar</guid>
      </item>
      <item>
         <title>Baz</title>
         <pubDate>Sun, 8 Sep 2019 00:00:00 GMT</pubDate>
         <link>https://andersmurphy.com/baz</link>
         <guid isPermaLink="true">https://andersmurphy.com/baz</guid>
      </item>
   </channel>
</rss>
```

This concludes this guide to generating HTML and XML in Clojure. The full example project can be found [here](https://github.com/andersmurphy/clj-cookbook/tree/master/generating-files/html-and-xml-example).
