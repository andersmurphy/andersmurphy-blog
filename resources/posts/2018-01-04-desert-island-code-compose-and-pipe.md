You awake a castaway on a desert island. After some time you come
across an ancient computation device, the programming of which might hold your
salvation!

The device, though limitless in computational power, for better or worse only
understands javascript. Alas this dialect of javascript doesn't have access to:
compose and pipe!

<!--more-->

A cruel fate indeed.

But wait, what if we were to write our own implementations of: pipe and compose?

### Pipe

What does pipe do?

Pipe allows left-to-right function composition. The leftmost function may have
any arity; but the remaining functions must be unary.

The `pipe` function takes a variable number of functions `...fns` and returns
a function that takes a single argument which we will call `seed`.

{% highlight javascript %}
const pipe = (...fns) => seed => console.log('do stuff here')
{% endhighlight %}

Then, we reduce over the functions `...fns` passing the previous output
`previousOutput` of each function into the next function until there are no
more functions left.

```javascript
const pipe = (...fns) => seed => fns.reduce(
  (previousOutput, fn) => fn(previousOutput),
  seed
)

const map = mapFunc => coll => coll.map(mapFunc)

pipe(map(x => x - 2), map(x => x * 2))([1, 2, 3]) // [-2,0,2]
```

### Compose

What does compose do?

Compose allows right-to-left function composition. The rightmost function may have
any arity; but the remaining functions must be unary. Basically, the same as pipe
just from right-to-left.

The `compose` function takes a variable number of functions and returns
a function that takes a single argument which we will call `seed`.

```javascript
const compose = (...fns) => seed => console.log('do stuff here')
{% endhighlight %}
```

Then, we reduce over the functions `...fns` passing the previous output
`previousOutput` of each function into the next function until there are no
more functions left.

```javascript
const compose = (...fns) => seed => fns.reduceRight(
  (previousOutput, fn) => fn(previousOutput),
  seed
)

const map = mapFunc => coll => coll.map(mapFunc)

compose(map(x => x - 2), map(x => x * 2))([1, 2, 3]) // [0,2,4]
```


### Bonus: Reduce Right

We saw how to write our own implementation of reduce in [this post](https://andersmurphy.com/2017/12/28/desert-island-code-reduce-map-and-filter/).
But how would we implement reduce right? Reduce right is almost identical
to reduce except it loops over the collection backwards.

Here's one way we could implement it:

```javascript
const reduceRight = (reduceFunc, startingValue, coll) => {
  let accumulator = startingValue
  for(let i = coll.length - 1; i >= 0; i--) {
    accumulator = reduceFunc(accumulator, coll[i])
  }
  return accumulator
}

reduceRight((acc, item) => acc + item, 0, [ 1, 2, 3, 4]) // 10
```

So there you have it. Pipe, compose and reduce right on a desert island.
