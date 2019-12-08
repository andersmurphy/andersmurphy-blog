Title: Ruby: functional programming

In one of my previous jobs I worked as a full stack engineer on a codebase with a Ruby backend and a Javascript/React frontend. Having used Clojure a fair bit in my spare time I was keen to code in a functional style. In Javascript this is relatively easy as it supports first class functions (functions as values). But at first glance this seems trickier in Ruby as it doesn't have first class functions.

### Lambda

Ruby might not have first class functions. But it does have `lambda`.

We can assign a `lanbda` to a value.

```Ruby
add = lambda {|x, y| x + y}
```

We can return a `lambda` from a function.

```Ruby
def identity (x)
  x
end

identity(add)
=> add lambda
```

We can store a `lambda` in a data structure.

```Ruby
a = [add]
a.first.call(4, 6)
=> 10
```

Working with `lambda` looks something like this.

```Ruby
add = lambda {|x, y| x + y}

identity = lambda {|x| x}

identity.call(add).call(1, 2)
=> 3
```

We can make those calls to `call` less verbose by using the `.()` shorthand.

```Ruby
add = lambda {|x, y| x + y}

identity = lambda {|x| x}

identity.(add).(1, 2)
=> 3
```

If writing `lambda` seems like a lot of work, we can use the `->` shorthand.

```Ruby
add = -> x,y {x + y}

identity = -> x {x}

identity.(add).(1, 2)
=> 3
```

We can use a `lambda` as a higher order parameter with `&` operator.

```Ruby
inc = -> x {x + 1}

[1, 2, 3, 4].map(&inc)
=> [2, 3, 4, 5]
```


We can pass methods as functions using `&` combined with the symbol of the method in this case `:even?`.

```ruby
[1, 2, 3, 4].(&:even?).reduce(:+)
=> 6
```

### Currying

 `lambda` even has built in currying.

```Ruby
add = -> x,y {x + y}

inc = add.curry.(1)

[1, 2, 3, 4].map(inc).reduce(:+)
=> 14
```

### Compose & Pipe

It's really useful to be able to compose functions. We can do this in Ruby by writing our own `comp` function with reduce. This will compose functions right to left.

```Ruby
add = -> x,y {x + y}
times = -> x,y {x * y}

comp = -> *fns {fns.reduce {|f, g| -> x {f.(g.(x))}}}

[1,2,3,4].map(&comp.(add.curry.(1), times.curry.(2)))
=> [3, 5, 7, 9]
```

If we want to compose left to right we can write our own `pipe` function.

```Ruby
pipe = -> *fns {fns.reverse.reduce {|f, g| -> x {f.(g.(x))}}}

[1,2,3,4].map(&pipe.(add.curry.(1), times.curry.(2)))
=> [4, 6, 8, 10]
```

Or since ruby `2.6` we can use `<<` or `>>`.

```Ruby
add = -> x,y {x + y}
times = -> x,y {x * y}

add_one_and_times_by_two = add.curry.(1) >> times.curry.(2)

add_one_and_times_by_two.(1)
=> 4

times_by_two_and_add_one = add.curry.(1) << times.curry.(2)

times_by_two_and_add_one.(1)
=> 3
```

We can also do inline composition.

```Ruby
add = -> x,y {x + y}
times = -> x,y {x * y}

[1,2,3,4].map(&(add.curry.(1) >> times.curry.(2) >> add.curry.(5)))
=> [9, 11, 13, 15]
```

Most of these features\* have been in Ruby since or before `1.9` which was released in 2007.  It turns out functional programming has been part of Ruby for a long time and might be a lot more idiomatic than you first think.

\* Except for `<<` and `>>` which were added in Ruby `2.6` released in 2018.
