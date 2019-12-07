Title: Ruby: first class functions

In one of my previous jobs I worked as a full stack engineer on a codebase with a Ruby backend and a Javascript/React frontend. Having used Clojure a fair bit in my spare time I was keen to code in a functional style. In Javascript this is relatively easy as it supports first class functions (functions as values). But at first glance this seems trickier in Ruby as it doesn't have first class functions... at least not officially.

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

identity(add) # => add lambda
```

We can store a `lambda` in a data structure.

```Ruby
a = [add]
a.first.call(4, 6) # => 10
```

Working with `lambda` looks something like this.

```Ruby
add = lambda {|x, y| x + y}

identity = lambda {|x| x}

identity.call(add).call(1, 2) # => 3
```

We can make those calls to `call` less verbose by using the `.()` shorthand.

```Ruby
add = lambda {|x, y| x + y}

identity = lambda {|x| x}

identity.(add).(1, 2) # => 3
```

If writing `lambda` seems like a lot of work, we can use the `->` shorthand.

```Ruby
add = -> x,y {x + y}

identity = -> x {x}

identity.(add).(1, 2) # => 3
```

We can use a `lambda` as a higher order parameter with `&` operator.

```Ruby
inc = -> x {x + 1}

[1, 2, 3, 4].map(&inc) # => [2, 3, 4, 5]
```


We can pass methods as functions using `&` combined with the symbol of the method in this case `:even?`.

```ruby
[1, 2, 3, 4].(&:even?).reduce(:+) # => 6
```

### Currying

 `lambda` even has built in currying.

```Ruby
add = -> x,y {x + y}

inc = add.curry.(1)

[1, 2, 3, 4].map(&:inc).reduce(:+) # => 14
```

Most of these features have been in Ruby since or before `1.9` which was released in 2007.  It turns out functional programming has been part of Ruby for a long time and might be a lot more idiomatic than you first think.
