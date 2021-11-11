Title: Clojure: code highlights for this website

When I changed the styling of this website I removed code highlights as part of an exercise in minimalism. Anyway, I recently read this awesome article about writing a [Clojure highlighter from scratch in 160 lines](https://blog.michielborkent.nl/writing-clojure-highlighter.html) which inspired me to add some very basic highlights back to this site. This post is about implementing my own Clojure highlighter from scratch in only 8 lines (and a fraction of the functionality).

I currently use monochrome highlights in my editor. I find it less distracting and it means I can use colour where I feel it matters (error, linter warnings etc). Thankfully this also greatly simplifies what this highlighter needs to support. All it needs to do make the symbol after def/defn/defmacro/ns etc bold.

Without further fanfare here's the highlighter:

```Clojure
(defn clojure-bold-defs [html]
  (str/replace html
               #"<code class=\"[Cc]lojure\">([\s\S]+?)</code>"
               (fn [[full-capture]]
                 (str/replace
                  full-capture
                  #"(&#40;def[a-z]* |ns )(\S+)"
                  "$1<strong>$2</strong>"))))
```

Lets break this code down. First we need to find the html code blocks that have a class `"clojure"`:

```Clojure
(defn clojure-bold-defs [html]
  (str/replace html
               #"<code class=\"[Cc]lojure\">([\s\S]+?)</code>"
               ...))
```

Then we need to replace any code that starts with `(def foo ..` with
`(def <strong>foo</strong> ..`:

```Clojure
(fn [[full-capture]]
                 (str/replace
                  full-capture
                  #"(&#40;def[a-z]* |ns )(\S+)"
                  "$1<strong>$2</strong>"))
```

We use `&#40;` which is the HTML coded character for `(` as that what our markdown to html converter returns.

That's all there is to it. That very same code generates the minimalist code highlights on this website.

The [full example can be found here](https://github.com/andersmurphy/andersmurphy-blog/blob/a98909a62c8f452667588c589e1af30a5291d951/src/core.clj#L141-L148).
