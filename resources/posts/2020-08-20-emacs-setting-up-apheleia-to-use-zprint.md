Title: Emacs: setting up Apheleia to use Prettier and Zprint

[Apheleia](https://github.com/raxod502/apheleia) is an awesome Emacs package for formatting code quickly using external command line tools like `prettier`. Not only is it fast but it also does a great job at maintaining point position relative to its surroundings (which has been one of my minor frustrations with other Emacs formatters) . Its also language agnostic so you can use it with any language that has a command line formatting tool. This post will cover setting up apheleia with [prettier](https://github.com/prettier/prettier) (for JavaScript) and [zprint](https://github.com/kkinnear/zprint) (for Clojure).

Though outside the scope of this post, for those that are interested, apheleia does some really impressive stuff under the hood with [RCS patches](https://tools.ietf.org/doc/tcllib/html/rcs.html#section4) and [dynamic programming algorithm for string alignment](https://en.wikipedia.org/wiki/Needleman%E2%80%93Wunsch_algorithm).

## Apheleia

Set up aphelia using [use-package](https://github.com/jwiegley/use-package):

```clojure
(use-package apheleia
  :straight (apheleia :host github :repo "raxod502/apheleia")
  :config
  (apheleia-global-mode t))
```

## Prettier

If you use JavaScript you can set up prettier:

```clojure
(use-package apheleia
  :straight (apheleia :host github :repo "raxod502/apheleia")
  :config
  (setf (alist-get 'prettier apheleia-formatters)
        '(npx "prettier"
              "--trailing-comma"  "es5"
              "--bracket-spacing" "true"
              "--single-quote"    "true"
              "--semi"            "false"
              "--print-width"     "100"
              file))
  (add-to-list 'apheleia-mode-alist '(rjsx-mode . prettier))
  (apheleia-global-mode t))
```

Notice we can pass in formatting defaults.

## Zprint

If use Clojure you can set up zprint:

```clojure
(use-package apheleia
  :straight (apheleia :host github :repo "raxod502/apheleia")
  :config
  (setf (alist-get 'clj-zprint apheleia-formatters)
        '("clj-zprint"
          "{:style [:community :justified] :map {:comma? false}} <"
          file))
  (add-to-list 'apheleia-mode-alist '(clojure-mode . clj-zprint))
  (apheleia-global-mode t))
```

Again we can pass in formatting defaults.

## Both

If you are fortunate enough to use both Javascript and Clojure you can set them up together:

```clojure
(use-package apheleia
  :straight (apheleia :host github :repo "raxod502/apheleia")
  :config
  (setf (alist-get 'prettier apheleia-formatters)
        '(npx "prettier"
              "--trailing-comma"  "es5"
              "--bracket-spacing" "true"
              "--single-quote"    "true"
              "--semi"            "false"
              "--print-width"     "100"
              file))
  (setf (alist-get 'clj-zprint apheleia-formatters)
        '("clj-zprint"
          "{:style [:community :justified] :map {:comma? false}} <"
          file))
  (add-to-list 'apheleia-mode-alist '(rjsx-mode . prettier))
  (add-to-list 'apheleia-mode-alist '(clojure-mode . clj-zprint))
  (apheleia-global-mode t))
```

The full example can be found in my Emacs config
[here](https://github.com/andersmurphy/.emacs.d/blob/15a040e522aa95ee74b510fda07e2c85065d0703/init.el#L721).

Checkout this post [for installing zprint with homebrew](https://andersmurphy.com/2020/08/18/homebrew-write-your-own-brew-formula.html).
