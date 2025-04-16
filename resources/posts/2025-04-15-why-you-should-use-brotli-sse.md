title: Why you should consider using brotli compression with SSE

Last week the fun little multiplayer GoL game I made got a lot of traffic from hackernews and survived. This blog post is a story about how Brotli compression saved me from myself and how it could save you too!

<iframe src="https://example.andersmurphy.com/star" title="Game of Life"
style="width: 100%;	aspect-ratio: 1/1;	max-width: 400px"></iframe>
<br/>
*This iframe is the live game, you can play it, tap away.*

I'm not going to go into massive detail about how the demo was built. You can [read more about it here](https://andersmurphy.com/2025/04/07/clojure-realtime-collaborative-web-apps-without-clojurescript.html).

The short version is it was sending down a lot of HTML every 200ms to all connected users. As comment pointed out on HN:

> "Sends down 2500 divs every 200ms to all connected cliends via compressed SSE."
> If I didn't know better, I'd say this was an April Fool's joke.

At the surface level this sound like a terrible idea and if you look at the network graph for my VPS it could have been:

![network graph](/assets/gol-network.png)

>1MBps is a lot... at 200:1 compression it's.... a lot a lot

Thankfully, constraints can drive innovation and for me that was compressing SSE streams.

## Why is compressing a stream better?

Compression works better on larger data sets as there is more likely to be duplication that can be forward/backward referenced. A continuous stream of data effectively condenses  multiple pages and page/states into a single large data set, rather than multiple response compressed separately. This is why you'll often see much larger compression ratios over streams than when compressing a single HTML/JSON response.

## Shared context window tuning for fun and profit

Compression algorithms often use a context window shared between the decompressor (client) and the compressor (server). In the case of gzip that window is a fixed 32kb. This represents the memory both the client and the server maintain for forward and sometime backward references. Unlike, gzip, brotli lets you tune this window. This can make a significant difference if your content has repetitions that are spaced far apart. In the case of a chaotic 2500 cell game of life this repetition will happen eventually. 

I found tuning compression from 32kb window to a 263kb window increased my compression ratio from 30:1 to 150-250:1 (increasing it further didn't do much). Not, only does this reduce network usage, it also reduces CPU usage on the client and server. I saw a server CPU usage drop of between x4-8 times less.

The game runs on a 4 core 8gb ram shared Hetzner VPS (at around 15$ per month). Here's what the CPU load looked like during the rush. 

![cpu graph](/assets/gol-cpu.png)

Without, tuning the context window this could have easily been x4-8 higher. Suddenly, you'd be looking at needing 12-24 core machine. In short without tuning the window my 15$ VPS would have been toast.

*Note: In terms of browser support Brotli is the most [widely supported compression algorithm after gzip](https://caniuse.com/?search=brotli).*

## Show me the code!

The code below is written Clojure and uses a [Java library for Brotli compression](https://github.com/hyperxpro/Brotli4j?tab=readme-ov-file). Hopefully, this is enough to give you a rough idea of how you would implement SSE compression in your backend language of choice.

```clojure
(defonce ensure-br
  (Brotli4jLoader/ensureAvailability))

(defn encoder-params [{:keys [quality window-size]}]
  (doto (Encoder$Parameters/new)
    (.setMode Encoder$Mode/TEXT)
    ;; LZ77 window size (0, 10-24) (default: 24)
    ;; window size is (pow(2, NUM) - 16)
    (.setWindow (or window-size 24))
    (.setQuality (or quality 5))))

(defn byte-array-out-stream ^ByteArrayOutputStream []
  (ByteArrayOutputStream/new))

(defn compress-out-stream ^BrotliOutputStream
  [^ByteArrayOutputStream out-stream & {:as opts}]
  (BrotliOutputStream/new out-stream (encoder-params opts)
    16384))

(defn compress-stream [^ByteArrayOutputStream out ^BrotliOutputStream br chunk]
  (doto br
    (.write  (String/.getBytes chunk))
    (.flush))
  (let [result (.toByteArray out)]
    (.reset out)
    result))
```

You can find [the full code here](https://github.com/andersmurphy/hyperlith/blob/master/src/hyperlith/impl/brotli.clj#L39).

## Update: Why not ZSTD?

ZSTD is not available on safari and because every iOS browser uses safari under the hood (even chrome/FF). That means ZSTD doesn't work for anyone who is using an iPhone. 

## Update: Why not GZIP?

GZIP is great for regular request/response. It already comes with java and/or your reverse proxy out of the box. For an 5-10% extra compression Brotli is not worth the extra dependency (including the native ones).

From what I've seen CPU usage of Brotli set to level 5 is better compression than Java's default GZIP and similar CPU. It's not much cheaper.

However, things change dramatically when you move to SSE. Brotli is much better at compressing streaming data than GZIP as it can do forward and backward references and was built with streaming from the ground up (streaming was bolted on to GZIP). So out of the box brotli gives you 2x better compression over streams than GZIP (that's a big deal). 

<img src="/assets/gzip-v-brotli.jpg" alt="gzip vs brotli" style="width:300px;"/>
<br/>

With context window tuning (which you can't do with GZIP) you can get 3x plus better compression. The context window also makes the CPU costs lower than GZIP when handling the same amount of data, as larger context means less data being compressed and more forward references being used less CPU (in exchange for memory).

## Conclusion

I hope this article has been useful (or at least amusing). The takeaway for me is: If you are using SSE, you should probably consider compressing that stream. If you are using SSE with long lasting connection that send a lot of data, you should really consider compressing that stream, especially if over time that data repeats (which it is likely to do).

**Discussion**

- [hackernews](https://news.ycombinator.com/item?id=43692354)

**Further Reading:**

- [How the game was built using Clojure and Datastar](https://andersmurphy.com/2025/04/07/clojure-realtime-collaborative-web-apps-without-clojurescript.html).
- [Direct link to the game](https://example.andersmurphy.com).
