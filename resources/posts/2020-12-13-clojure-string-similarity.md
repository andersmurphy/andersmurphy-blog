Title: Clojure: string similarity

Sometimes you want to compare strings for similarity. To do this we can use [cosine similarity](https://en.wikipedia.org/wiki/Cosine_similarity) to determine how similar two strings are to each other.

If we split a string into individual words (tokens) we can then assign each token a number, creating a vector of numbers for that string. We can then calculate the angle between two vectors. If the angle is small the strings are similar if it's large they are dissimilar.

We can apply this idea to compare two bodies of text for similarity. This can be useful, for example generating a list of related articles at the bottom of a blog post.

## Cosine similarity

We split the string into vectors of tokens. Create a vector of all the unique tokens from both strings;`all-tokens`. Create a vector for each of the strings which contains the count of each token from `all-tokens` that appears in that string. Finally we put these vectors into the cosine formula `(/ (dot a b) (* (mag a) (mag b)))`.

```Clojure
(require '[clojure.string :as str])

(defn split-tokens [input]
  (-> (str/lower-case input)
      (str/split #"\W+")))

(defn mag [v]
  (->> (map #(* % %) v)
       (reduce +)
       Math/sqrt))

(defn dot [a b]
  (->> (map * a b)
       (reduce +)))

(defn cosine [a-string b-string]
  (let [a-tokens (split-tokens a-string)
        b-tokens (split-tokens b-string)
        all-tokens (distinct (concat a-tokens b-tokens))
        a-vector (map #(get (frequencies a-tokens) % 0) all-tokens)
        b-vector (map #(get (frequencies b-tokens) % 0) all-tokens)]
    (/ (dot a-vector b-vector)
       (* (mag a-vector) (mag b-vector)))))
```

The cosine function outputs an angle value between 0 and 1. Strings that have an angle value closer to 1 are similar and those that have one closer to 0 are dissimilar.

```Clojure
(cosine "Clojure: sorting by key" "Clojure: sorting by value")

=> 0.75

(cosine "Ruby: functional programming" "Clojure: sorting by value")

=> 0.0
```

I've included this version of the cosine function that prints the workings. For those who want to see the step by step evaluation.

```Clojure
(defn cosine [a-string b-string]
    (let [a-tokens (split-tokens a-string)
          b-tokens (split-tokens b-string)
          all-tokens (distinct (concat a-tokens b-tokens))
          a-vector (map #(get (frequencies a-tokens) % 0) all-tokens)
          b-vector (map #(get (frequencies b-tokens) % 0) all-tokens)]
      [[:a-tokens a-tokens]
       [:b-tokens b-tokens]
       [:all-tokens all-tokens]
       [:a-vector a-vector]
       [:b-vector b-vector]
       [:dot-result (dot a-vector b-vector)]
       [:mag-a-vector (mag a-vector)]
       [:mag-b-vector (mag b-vector)]
       [:result (/ (dot a-vector b-vector)
                   (* (mag a-vector) (mag b-vector)))]]))


(cosine "Clojure: sorting by key" "Clojure: sorting by value")

=>
[[:a-tokens ["clojure" "sorting" "by" "key"]]
 [:b-tokens ["clojure" "sorting" "by" "value"]]
 [:all-tokens ("clojure" "sorting" "by" "key" "value")]
 [:a-vector (1 1 1 1 0)]
 [:b-vector (1 1 1 0 1)]
 [:dot-result 3]
 [:mag-a-vector 2.0]
 [:mag-b-vector 2.0]
 [:result 0.75]]
```

## Stopwords

Stopwords are common words which give little to no meaning words: I, you, me, am etc.

If we don’t remove these stopwords the angle between vectors will be greater for strings with these words. This can lead to vectors being similar when they share stopwords despite not conveying the same information. Conversely similar vectors that don't share the same stopwords can be marked dissimilar.

```Clojure
(cosine "I am amazing" "I am a robot")

=> 0.5773502691896258
```

These strings do not convey similar meaning but the result suggests they do.

```Clojure
(def stopwords
"Not a complete list of english stop words but good enough for this example"
  #{"i" "me" "my" "myself" "we" "our" "ours" "ourselves" "you"
    "your" "yours" "yourself" "yourselves" "he" "him" "his"
    "himself" "she" "her" "hers" "herself" "it" "its" "itself"
    "they" "them" "their" "theirs" "themselves" "what" "which"
    "who" "this" "that" "these" "those" "am" "is" "are" "was"
    "were" "be" "been" "being" "have" "has" "had" "having"})

(defn cosine [a-string b-string]
  (let [a-tokens (remove stopwords (split-tokens a-string))
        b-tokens (remove stopwords (split-tokens b-string))
        all-tokens (distinct (concat a-tokens b-tokens))
        av (map #(get (frequencies a-tokens) % 0) all-tokens)
        bv (map #(get (frequencies b-tokens) % 0) all-tokens)]
    (/ (dot av bv)
       (* (mag av) (mag bv)))))
```

By filtering the tokens by stopwords we get more accurate results.

```Clojure
(cosine "I am amazing" "I am a robot")

=> 0.0
```

These strings do not convey similar meaning.

That covers using cosine similarity to compare strings. This can be really useful for finding similarity between strings. It's worth pointing out it isn't limited to strings you could use it to compare any data you can represent as a vector (mathematical term not the Clojure collection type).
