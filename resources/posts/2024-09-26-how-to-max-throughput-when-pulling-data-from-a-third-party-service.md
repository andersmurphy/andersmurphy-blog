title:  How to max throughput when pulling data from a third party service

Say you have an app. When a user signs up to your app you need to pull large amount of data from a third party service. The faster you can do this, the more responsive your app can be, the less your user has to wait, the better the user experience. In this post I'll break down the key things to think about when approaching this problem.

## How much data? How long?

First, get a feel for is how much data you will need to sync and how long it might take. As an example I'm going to draw from a recent project. On average for each user that connected to our app we needed to get the signature of their last 100 transactions and then pull the data for those transactions.  

We can get the transaction signatures in batches of 20 but we have to pull the transaction data for each transaction separately. 

Each request takes 1 seconds. 

If we do this all sequentially we need to make 5 requests to get the signatures and 100 requests to get the transaction data. 

So 105 requests would take 105 seconds.

## Don't make the long tail wait forever

The above assumes the average user has 100 transactions, what happen there's an outlier. Say a user with 1000 transactions? That would take 1050 seconds! 17 minutes. If that's a possibility you need a plan for informing the users they should come back later, or notify them when the results are finished. 

Also think about what that would do to your system as a whole and the experience of other users if a single users is hogging all the requests.

## Rate limits

So we've got an idea for how long things take if we run things sequentially. Next we need to find out the rate limit of the third party service. Let's say they say you can make 1000 requests a second. So the theoretical limit for how fast you can sync the average user is 10.5 seconds. 

Keep in mind most services are at beast economical with the truth when it comes to their rate limits and/or leave key details out about their implementation. 

## Batch

Before we dive into the complexity of getting max throughput check to see if there's any way to batch your requests. In our example if we could get the data for 10 transactions in a single requests, we could potentially go 10 times as fast. Sometimes this can be enough and you can skip all the complexity of concurrency.

## Idempotency

Now lets think about what happens when things go wrong and our syncs fails half way through the process and we have to restart the sync. Ideally, we don't want to have to redo a bunch of work or sync data we already have. This would eat into time and our rate limit. 

The key thing here, is we don't want to request data we already have and ideally we want to write data as soon as we have it, or at least in the smallest batch sizes we can get away with without hammering performance.

A good rule of thumb is you want to be able to write a database query that gives you all un-synced transactions for a user.

## Backoff and retry

What happens when we exceed the rate limit? Ideally, the third party service returns a 429 and tells us when we can try again. If that's the case we need a mechanism for not crashing the job and trying again. Sometimes, the simpler approach, assuming our sync is idempotent is to have a catch up job that retries the sync later for that user. 

Another thing to keep in mind is you don't want a single failure stopping all your other work in progress i.e if we fail to pull a single transaction we shouldn't stop all the other transactions from being pulled.

## Global concurrent rate limit

We need a mechanism that rate limits our requests, most languages will have a library that handles this. Ideally, it should thread safe so multiple threads can use it concurrently. See [this post for an implementation with semaphores in Clojure](https://andersmurphy.com/2024/05/06/clojure-managing-throughput-with-virtual-threads.html).

## Concurrency

After doing the maths we've decided that we need some form of concurrency to get more throughput on our sync. Good thing we have a thread safe rate limiter.

Are threads expensive or cheap? If your using Go, Java or Clojure rejoice you have cheap real threads (parallelism). If your are using Ruby, Python, Lua or Node you'll have to use a combination async and/or queues and workers. Thankfully, in this case async is fine as requests are IO bound so mostly involve waiting rather than compute.

What requests can be done concurrently? In our example getting the signatures needs to be done sequentially as the results are paginated and you need the last signature of your current page to get the next page. However, getting the transaction data can be done concurrently as each transaction is independent. 

Here, we effectively want to split our sequence of tasks into multiple concurrent tasks. [See this post again for an in Clojure](https://andersmurphy.com/2024/05/06/clojure-managing-throughput-with-virtual-threads.html)

## Unordered

Unordered concurrency can help minimizing latency by dealing with results as soon as they became available. It also helps you maximise data transformation/write throughput by saturating those resources. 

## Bottlenecks 

In a perfect world you want the third party service to be the bottleneck. But, this isn't always the case, sometimes it can be lack of concurrency or write speed to the database. This is where back pressure comes in. If your database is struggling to keep up you will need to mechanism to slow down the requests or your system might end up running out of resources. 

In our case we found we could get much faster writes by reducing the concurrency when it came to inserts. We'd merge the results from all our requests into a single queue that would batch insert into the database rather than just hammering it with thousands of concurrent inserts.

Effectively, in simple terms, we had a queue of users jobs which would fan out to the maximum number of concurrent requests to saturate the rate limit and then fan in to a single queue to write to the database. 

## Sampling

If you are using a unordered concurrency/queue, i.e you can process results as soon as they are ready out of order, then sampling can make a big difference. By showing results to the user when you have 99% of the data rather than 100% of the data you can often dramatically increase the speed at which they see results. This is because sampling protects you from outliers in the third parties response time. 

Say we make 5 request and they take 1s, 1s , 1s, 2s, 30s and we show the results after 5/5 requests complete, then the user will wait 30s. If we make the same requests but show the results after 4/5 requests complete, then the user will wait 2s.

Of course you can only do this in cases where you don't need all the data to show something useful to the user.

In our case sampling 99% of the results reduced the average wait time by 50%.

## Don't starve

Make sure your thread safe rate limiter is FIFO to prevent starvation, if not you can end up in a situation where a user is waiting forever because their tasks are always at the back and never processed. 

## Conclusion

Hopefully, this post provided a good list of things to think about when trying to maximise throughput when pulling data from third parties. Depending on the context you might only need to implement some of these. The most important one in my experience is making sure your syncs are idempotent as that mean you can handle partial sync and recover.
