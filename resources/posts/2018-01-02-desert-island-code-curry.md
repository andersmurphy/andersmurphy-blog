You awake a castaway on a desert island. After some time you come
across an ancient computation device, the programming of which might hold your
salvation!

The device, though limitless in computational power, for better or worse only
understands javascript. Alas this dialect of javascript doesn't have access to:
curry!

<!--more-->

A cruel fate indeed.

But wait, what if we were to write our own implementation of: curry?

### Curry

Currying is a technique for translating the evaluation of a function that takes
multiple arguments (multiple arity/n-ary function) into evaluating a sequence of
functions, each with a single argument (single arity/unary function).

[ECMAScript 6](https://www.ecma-international.org/ecma-262/6.0/) has an elegant way of currying functions using multiple arrow functions.

```javascript
const curryMap = mapFunc => coll => map(mapFunc, coll)
const addOneToEachElement = curryMap(x => x + 1)

addOneToEachElement([ 1, 2, 3, 4, 5]) // [ 2, 3, 4, 5, 6]
```

The above approach works fine for converting binary functions into unary
functions. However, this can quickly get cumbersome for n-ary functions. This is
one of the reasons libraries like [Lodash](https://github.com/lodash/lodash) and [Ramda](https://github.com/ramda/ramda) have a curry function.

So how would we go about implementing our own curry function?

Well, our goal is to eventually pass all the supplied arguments into the original
function. So if we have a function called `f` and passed in the arguments `a`,
`b` and `c` we would want to call `f(a, b, c)`. In other words:

`a => b => c => f(a, b, c)`

When do we want to pass in all the arguments to the original function?

When the number of arguments supplied is the same as the number of arguments required
by the function. So if we accumulate our arguments in an array, when that
array is the same size as the number of arguments the function requires, we
can call the function with the accumulated arguments.

In javascript, we can use `functionName.length` to find out how many arguments a
function takes. Then the spread syntax `...` can be used to expand an array in
place into multiple arguments for a function. This gives us the start of a [recursive](https://en.wikipedia.org/wiki/Recursion_(computer_science))
function whose base case (a condition in which the function doesn't recur) would
look like this:

```javascript
if (args.length === func.length) {
  return func(...args)
}
```

Now we can wrap the above in a function called `curry`. We know that this function needs to
take a function `func` and return a function that takes a variable number of
arguments `...nextArgs`.

```javascript
const curry = func => (...nextArgs) => {
  const args = nextArgs
  if (args.length === func.length) {
    return func(...args)
  }
}

const zeroArg = () => 0
curry(zeroArg)() // 0

const oneArg = a => a
curry(oneArg)(1) // 1

const twoArgs = (a, b) => a - b
curry(twoArgs)(2, 1) // 1
```

The above works for the base case when all the arguments are supplied in one go.

Next, we need an accumulator to collect the previous arguments passed into the
`curry` function. We can do this by concatenating `nextArgs` to `previousArgs`. The
starting value of `previousArgs` is an empty array (as there are no previous arguments):

```javascript
const curry = (func, previousArgs = []) => (...nextArgs) => {
  const args = previousArgs.concat(nextArgs)
  if (args.length === func.length) {
    return func(...args)
  }
}

const twoArgs = (a, b) => a - b
curry(twoArgs)(2, 1) // 1
```

Finally, we need to add the recur case where the `curry` function recurs
taking the accumulated value `args`.

```javascript
const curry = (func, previousArgs = []) => (...nextArgs) => {
  const args = previousArgs.concat(nextArgs)
  if (args.length === func.length) {
    return func(...args)
  } else {
    return curry(func, args)
  }
}

const zeroArg = () => 0
curry(zeroArg)() // 0

const oneArg = a => a
curry(oneArg)(1) // 1

const twoArgs = (a, b) => a - b
curry(twoArgs)(2)(1) // 1
curry(twoArgs)(2, 1) // 1

const threeArgs = (a, b, c) => a - b + c
curry(threeArgs)(3)(2)(1) // 2
curry(threeArgs)(3)(2, 1) // 2
curry(threeArgs)(3, 2)(1) // 2
curry(threeArgs)(3, 2, 1) // 2
```

So there you have it. Curry on a desert island.
