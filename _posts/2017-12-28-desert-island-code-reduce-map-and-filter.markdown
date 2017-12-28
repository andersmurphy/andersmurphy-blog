---
layout: post
title:  "Desert Island Code: reduce, map and filter"
---
You awake, a castaway on a desert island. After some time you come
across a ancient computation device. The programming of which might hold your
salvation!

<!--more-->

The device though limitless in computational power, for better or worse only
understands javascript. Alas this dialect of javascript doesn't have map, reduce or
filter!

A cruel fate indeed.

But wait what if we were to write our own implementations of: map, filter and reduce?

### Reduce

Map and filter can be built with reduce, so lets start with implementing that.
Good old for loop to the rescue.

{% highlight javascript %}
function reduce(reduceFunction, startingValue, collection) {
  var accumulator = startingValue
  for(var i = 0; i < collection.length; i++) {
    accumulator = reduceFunction(accumulator, collection[i])
  }
  return accumulator
}

reduce((acc, item) => acc + item, 0, [ 1, 2, 3, 4]) // 10
{% endhighlight %}

### Map

Whenever you want to derive a new value from
a collection you can use reduce. Let see how we can use it to implement Map.

{% highlight javascript %}
function map(mapFunction, collection) {
  return reduce(
    function(accumulator, item) {
      accumulator.push(mapFunction(item))
      return accumulator
    },
    [],
    collection
  )
}

map(x => x + 1, [ 1, 2, 3, 4, 5]) // [ 2, 3, 4, 5, 6]
{% endhighlight %}

### Filter

Finally lets implement filter.

{% highlight javascript %}
function filter(filterFunction, collection) {
  return reduce(
    function(accumulator, item) {
      if(filterFunction(item)) {
        accumulator.push(item)
      }
      return accumulator
    },
    [],
    collection
  )
}

filter(x => x === 3, [ 3, 2, 3, 4, 3]) // [3, 3, 3]
{% endhighlight %}

So there you have it. Reduce, map, and filter on a desert island.
