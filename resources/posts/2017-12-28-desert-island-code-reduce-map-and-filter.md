You awake a castaway on a desert island. After some time you come
across an ancient computation device. The programming of which might hold your
salvation!

The device, though limitless in computational power, for better or worse only
understands javascript. Alas this dialect of javascript doesn't have map, reduce or
filter!

<!--more-->

A cruel fate indeed.

But wait, what if we were to write our own implementations of: map, filter and reduce?

### Reduce

Map and filter can be built with reduce, so lets start with implementing that.
Good old for loop to the rescue.

```javascript
const reduce = (reduceFunc, startingValue, coll) => {
  let accumulator = startingValue
  for(let i = 0; i < coll.length; i++) {
    accumulator = reduceFunc(accumulator, coll[i])
  }
  return accumulator
}

reduce((acc, item) => acc + item, 0, [ 1, 2, 3, 4]) // 10
```

### Map

Whenever you want to derive a new value from
a collection you can use reduce. Let see how we can use it to implement Map.

```javascript
const map = (mapFunc, coll) => reduce(
  (accumulator, item) => {
    accumulator.push(mapFunc(item))
    return accumulator
  },
  [],
  coll
)

map(x => x + 1, [ 1, 2, 3, 4, 5]) // [ 2, 3, 4, 5, 6]
```

### Filter

Finally, lets implement filter.

```javascript
const filter = (filterFunc, coll) => reduce(
  (accumulator, item) => {
    if(filterFunc(item)) {
      accumulator.push(item)
    }
    return accumulator
  },
  [],
  coll
)

filter(x => x === 3, [ 3, 2, 3, 4, 3]) // [3, 3, 3]
```

So there you have it. Reduce, map, and filter on a desert island.
