Title: Emacs: the joy of reducing workflow friction with elisp

Emacs is an interactive, self-documenting and extensible elisp interpreter. This makes it surprisingly enjoyable to extend. It goes something like this: you notice some friction when using a command, you use Emacs' self-documenting features to learn about the command, you investigate the source, you write some elisp, you evaluate it and you try the new and improved command out without needing to restart Emacs.

Over time you get more adventurous as your knowledge of Emacs and elisp grows. Your write commands that reach further, you put up with less and less friction after all this is your development environment why not make yourself at home? You've also somehow become surprisingly good at elisp. For me this is the magic of Emacs.

Let's walk through an example.

##  Identify friction

You know when you copy something with `ctrl+v/cmd+v` and realise too late that you've now lost the previous thing you had copied? Well Emacs doesn't have that problem because all your copies get added to a list that you can cycle through by pressing `M-y` (`alt+y/opt+v`). This is a fantastic feature, but it only work when you press it straight after pasting some text. In this example that awkwardness is the friction we want to remove.

## Imagine how it should work

What if when we called the paste command consecutively it started cycling through the list of copies? Rather than inserting another copy.

What behaviour would this proposed solution interfere with? Pasting the same thing multiple times in a row? How often do you paste the same thing multiple times in a row? In my case almost never, even then I would probably issue a different command between pastes. So in theory this will be a net improvement to my workflow (it doesn't always play out like this in practice).

## Explore documentation and source

Lets find out what function is called when we type `M-y`. We can ask Emacs to describe a key by typing `C-h k` followed by the keys we want to describe, in this case `M-y`. This will bring up a help buffer with useful information about that key.

```
M-y runs the command yank-pop (found in global-map), which is an
interactive compiled Lisp function in ‘simple.el’.
```

`yank-pop` is the name of the function called when we press `M-y`. `yank` is Emacs name for paste. Emacs has arcane names for things as many of the names we use in software today like copy and paste were not standard when these features were implemented in Emacs.

This help buffer also has a direct link to the function in the `simple.el` source file. If we follow that link we will be able to see the elisp implementation.

```elisp
(defun yank-pop (&optional arg)
  "Replace just-yanked stretch...
  (interactive "p")
  (if (not (eq last-command 'yank))
      ...))
```

From this source code we can see how to call different functions based on the last command. Let's see the documentation for `last-command` by typing `C-h v last-command`.

```
...

The last command executed.
Normally a symbol with a function definition, but can be whatever was found
in the keymap, or whatever the variable ‘this-command’ was set to by that
command.

...
```

We now have enough understanding to start writing some elisp code.

##  Write some elisp

Putting this all together we can create a new function called `yank++`. We do this in the `*scratch*` buffer. This is a elisp buffer for writing, evaluating and testing arbitrary elisp code.

```elisp
(defun yank++ ()
  "Like 'yank'. But calling 'yank' again will call 'yank-pop'."
  (interactive)
  (if (member last-command '(yank yank-pop))
      (yank-pop)
    (yank)))
```

This function calls `yank-pop` if the value of `last-command` is a `member` of the list `(yank yank-pop)` i.e the last command was `yank` or `yank-pop`. Otherwise it calls `yank`.

##  Evaluate, bind and test the function

Place the cursor at the end of the `yank++` function (the `|` symbol below represents the cursor) and type `C-x C-e` to evaluate the code. This will 'load' this new function into Emacs.

```elisp
(defun yank++ ()
  "Like 'yank'. But calling 'yank' again will call 'yank-pop'."
  (interactive)
  (if (member last-command '(yank yank-pop))
      (yank-pop)
    (yank)))|
```

To bind the function to a key write the following elisp code and evaluate it like we did above with `C-x C-e`.

```elisp
(global-set-key (kbd "C-y") 'yank++)
```

Now try pasting something with `C-y` and see what happens when you press it multiple times. You should see it cycle through all the things you have copied.

Once you are happy with the results you can persist these changes by adding both the `yank++` function and the key-binding to your `init.el` file.

##  Profit

This example barely scratches the surface of what you can do when extending Emacs. But hopefully it still illustrates a valuable point.

Emacs is great at reducing friction because there is so little friction to extending Emacs. The distance between an idea and a working implementation is so short you barely have to reach. In Emacs friction is only a problem so long as you don't notice it. The minute you do you can spend a few minute writing elisp and make it disappear.
