Title: Clojure: removing namespace from keywords in response middleware

Namespaced keywords let you add a namespace to a keyword `:id` -> `:user/id`, this is a really nice feature that prevents collisions when merging two maps. For example merging  `{:account/id "foo"}` with  `{:user/id "bar"})` would give you `{:account/id "foo" :user/id "bar"}`. This lets you avoid unnecessary data nesting and keep your data flat. However, by the time this data gets to the edge of your system, where it meets an external system that expects json, you will often want to remove these namespaces. This post shows you how to write a middleware that automates the removal of namespaces from keywords.

We can remove the namespace from a keyword with the following code.

```
(-> :foo/bar name keyword)

=>
:bar
```

To remove all namespaces in an arbitrarily nested piece of data we can use `clojure.walk/postwalk`.

```
(defn transform-keys
  [t coll]
  (clojure.walk/postwalk (fn [x] (if (map? x) (update-keys x t) x)) coll))
```

As of Clojure as of `1.11.0` we can now use  `update-keys` (without having to roll our own implementation).

Now for the middleware. Ring defines middleware as a function of type `handler & args => request => response`.

```
(defn remove-namespace-keywords-in-response-middleware [handler & _]
  (fn [req]
    (let [resp (handler req)]
      (cond-> resp
        (comp map? :body) (update :body
                                  (partial transform-keys
                                           (comp keyword name)))))))
```

`cond->` is a nice way to conditionally do some things to a map (unlike `cond`, `cond->` doesn't short circuit after the first true expression). The `cond->` macro is really useful for conditional operations on data. In this case we only want to remove namespaces if the response has a body.

Thanks to this middleware we no longer have to remember to remove namespaces form keywords when returning json responses form our API.

It is worth pointing out that the if the keywords without namespaces are not unique they will get overwritten. This normally isn't an problem as by the time you are structuring your data for a json response it will be nested.

```
(update-keys
  {:user/id "foo" :accont/id "bar"}
  (comp keyword name))

=> {:id "bar"}
```
